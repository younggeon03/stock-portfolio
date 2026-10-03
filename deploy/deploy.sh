#!/usr/bin/env bash
# 새 이미지로 바꾸고, 헬스체크가 통과하지 않으면 바로 앞 버전으로 되돌린다.
#
#   ./deploy.sh sha-1e9845b   그 커밋의 이미지로 (GitHub Actions 가 부르는 형태)
#   ./deploy.sh               main 의 최신
#   ./deploy.sh --rollback    바로 앞 버전으로
#
# 상태 파일 두 개로 "지금"과 "바로 앞"을 기억한다: .deployed-tag, .previous-tag
set -euo pipefail
cd "$(dirname "$0")"

REPO="${IMAGE_REPO:-ghcr.io/younggeon03/stock-portfolio}"
# compose 가 .env 에서 읽는 값과 같은 포트를 봐야 한다
MGMT_HOST_PORT="${MGMT_HOST_PORT:-$(grep -E "^MGMT_HOST_PORT=" .env 2>/dev/null | cut -d= -f2 || true)}"
HEALTH_URL="http://127.0.0.1:${MGMT_HOST_PORT:-8081}/actuator/health/readiness"
# 앱은 DB 연결·Flyway 검사까지 끝나야 readiness 가 UP 이다. 작은 서버에서 40~90초 걸린다
TIMEOUT="${HEALTH_TIMEOUT:-240}"

log() { echo "[$(date '+%F %T')] $*"; }

CURRENT="$(cat .deployed-tag 2>/dev/null || true)"
PREVIOUS="$(cat .previous-tag 2>/dev/null || true)"

if [ "${1:-}" = "--rollback" ]; then
    [ -n "$PREVIOUS" ] || { log "되돌릴 앞 버전이 없습니다"; exit 1; }
    TAG="$PREVIOUS"
else
    TAG="${1:-main}"
fi

# COMPOSE_PROFILES=monitoring 이면 compose 가 prometheus·grafana 를 서비스로 본다.
# 출력을 변수에 받고 나서 grep 한다. 파이프로 바로 grep -q 에 넘기면 grep 이 먼저 끝날 때 compose 가 SIGPIPE 를 받고,
# pipefail 때문에 "꺼짐" 으로 잘못 읽힐 수 있다
monitoring_enabled() {
    local services
    services="$(docker compose config --services 2>/dev/null)" || return 1
    grep -qx prometheus <<<"$services"
}

# .env 의 KEY 가 채워져 있나.
# 빈 것으로 보는 경우: 값 없음, 공백만, 빈 따옴표("" ''), 공백 뒤 #(env.example 의 설명을 안 지우고 남긴 것).
#   compose 는 "KEY=   # 설명" 을 '# 설명' 이라는 값으로 읽는다. 예전 검사(.+)는 이걸 통과시켜
#   로그인 비밀번호가 설명 문장이 될 뻔했다(관측 PR 리허설에서 발견).
#   OWNER_PASSWORD="" 도 예전 검사를 통과해 로그인이 꺼진 채 뜰 수 있었다(검수에서 발견).
# 정상으로 보는 경우: "KEY=#abc" 처럼 # 이 바로 붙은 값. compose 도 값으로 읽는 정상 비밀번호다
has_value() {
    local line v
    line=$(grep -E "^$1=" .env | tail -n 1) || return 1
    v=${line#*=}
    case "$v" in
        ""|'""'|"''") return 1 ;;
    esac
    if [[ "$v" =~ ^[[:space:]]*$ || "$v" =~ ^[[:space:]]+# ]]; then
        return 1
    fi
    return 0
}

# ── 띄우기 전 확인 ──
# 운영에서 빠지면 조용히 위험해지는 값들. 에러가 안 나고 "열린 채로" 돈다
preflight() {
    [ -f .env ] || { log ".env 가 없습니다. env.example 을 복사해 채우세요"; exit 1; }
    local missing=()
    for k in DOMAIN COMPOSE_DB_PASSWORD COMPOSE_DB_ROOT_PASSWORD OWNER_PASSWORD TOKEN_ENCRYPTION_KEY; do
        has_value "$k" || missing+=("$k")
    done
    # 관측(Grafana)을 켠 서버에서만 필수. 1GB 서버처럼 관측을 끈 곳에서는 이 값 없이도 배포돼야 한다.
    # 켜졌는지는 compose 에게 묻는다(COMPOSE_PROFILES 가 .env 에 있든 셸 환경에 있든 compose 가 같은 규칙으로 읽는다)
    if [ ${#missing[@]} -eq 0 ] && monitoring_enabled; then
        has_value GRAFANA_ADMIN_PASSWORD || missing+=("GRAFANA_ADMIN_PASSWORD")
    fi
    # OWNER_PASSWORD 가 비면 로그인이 꺼져 내 잔고가 공개된다(개발 모드). 서버에서는 절대 안 된다
    grep -qE '^COOKIE_SECURE=true' .env || missing+=("COOKIE_SECURE=true")
    grep -qE '^SWAGGER_ENABLED=false' .env || missing+=("SWAGGER_ENABLED=false")
    if [ ${#missing[@]} -gt 0 ]; then
        log ".env 에 빠진 값: ${missing[*]}"
        exit 1
    fi
}

healthy() {
    # 잠든 시간만 세면 curl 이 기다린 시간이 빠져 실제로는 1.5배 넘게 기다린다(리허설에서 60초가 92초). 벽시계로 잰다
    local deadline=$((SECONDS + TIMEOUT)) dead=0
    while [ "$SECONDS" -lt "$deadline" ]; do
        if curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        # 뜨자마자 죽고 재시작을 반복하는 버전은 끝까지 기다릴 필요가 없다. 세 번 연속이면 바로 실패로 본다
        case "$(docker compose ps -a app --format '{{.State}}' 2>/dev/null)" in
            restarting|exited) dead=$((dead + 1)) ;;
            *) dead=0 ;;
        esac
        [ "$dead" -ge 3 ] && { log "앱이 계속 죽습니다. 기다리지 않고 실패로 봅니다"; return 1; }
        sleep 5
    done
    return 1
}

# stock-portfolio:current 를 그 태그로 옮기고 앱만 다시 띄운다. DB·Caddy 는 건드리지 않는다
switch_to() {
    docker tag "$REPO:$1" stock-portfolio:current
    # 이름(current)은 같아도 가리키는 이미지가 바뀌었으니 컨테이너를 새로 만든다
    docker compose up -d --no-deps --force-recreate app
}

preflight
log "배포 시작: $TAG (지금: ${CURRENT:-없음})"

docker compose pull --quiet mysql caddy
docker pull --quiet "$REPO:$TAG" >/dev/null
# 처음 배포면 DB·Caddy 도 같이 띄운다. 이미 떠 있으면 아무것도 안 한다.
# 서비스를 이름으로 집는다. 그냥 up -d 면 관측 컨테이너까지 띄우다 실패할 때 앱 배포가 같이 멈춘다(관측은 아래에서 따로)
if [ -z "$CURRENT" ]; then
    docker tag "$REPO:$TAG" stock-portfolio:current
    docker compose up -d mysql caddy app
else
    # compose.yml 에서 DB·Caddy 설정이 바뀌었으면 반영한다 (안 바뀌었으면 아무것도 안 한다)
    docker compose up -d --no-recreate mysql >/dev/null
    docker compose up -d --no-deps caddy >/dev/null
    # Caddyfile 은 파일만 바뀌어서 compose 가 모른다. 끊김 없는 reload 로 매번 다시 읽힌다
    docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null
    switch_to "$TAG"
fi

# ── 관측(선택) ── 켜진 서버에서만. 여기서 무엇이 실패해도 앱 배포는 계속한다(set -e 에 걸리지 않게 || 로 받는다)
if monitoring_enabled; then
    # 컨테이너가 새로 만들어졌는지 보려고 앞뒤 ID 를 비교한다
    prom_before="$(docker compose ps -q prometheus 2>/dev/null || true)"
    am_before="$(docker compose ps -q alertmanager 2>/dev/null || true)"
    # compose.yml 이 안 바뀌었으면 아무것도 안 한다(재생성 없음). 바뀌었으면 그 컨테이너만 다시 만든다
    docker compose up -d --no-deps prometheus grafana alertmanager >/dev/null || log "경고: 관측 컨테이너를 못 띄움 (앱 배포는 계속)"
    prom_after="$(docker compose ps -q prometheus 2>/dev/null || true)"
    am_after="$(docker compose ps -q alertmanager 2>/dev/null || true)"
    # prometheus.yml·alerts.yml 은 파일만 바뀌어서 compose 가 모른다. 같은 컨테이너면 SIGHUP 으로 다시 읽힌다.
    # 방금 새로 만든 컨테이너에는 보내지 않는다. 신호 처리기를 달기 전에 받으면 프로세스가 그냥 죽는다(어차피 새 파일을 읽고 떴다)
    if [ -n "$prom_before" ] && [ "$prom_before" = "$prom_after" ]; then
        docker compose kill -s SIGHUP prometheus >/dev/null 2>&1 || true
    fi
    # Alertmanager 설정은 시작할 때 만들어진다(텔레그램 값 → /tmp). 그래서 리로드가 아니라 재시작이 맞다.
    # 상태(보낸 기록)는 볼륨에 있어 재시작해도 같은 경보를 또 보내지 않는다
    if [ -n "$am_before" ] && [ "$am_before" = "$am_after" ]; then
        docker compose restart alertmanager >/dev/null 2>&1 || true
    fi
fi

if healthy; then
    if [ "$TAG" != "$CURRENT" ] && [ -n "$CURRENT" ]; then echo "$CURRENT" > .previous-tag; fi
    echo "$TAG" > .deployed-tag
    log "성공: $TAG"
    # 이미지가 쌓여 디스크를 채우지 않게. 지금과 바로 앞 버전은 남긴다 (롤백용)
    keep="$TAG $(cat .previous-tag 2>/dev/null || true) main"
    docker images "$REPO" --format '{{.Tag}}' | while read -r t; do
        case " $keep " in *" $t "*) ;; *) docker rmi "$REPO:$t" >/dev/null 2>&1 || true ;; esac
    done
    exit 0
fi

log "실패: $TAG 가 ${TIMEOUT}초 안에 준비되지 않았습니다. 앱 로그 끝부분:"
docker compose logs --no-color --tail 60 app || true

if [ -n "$CURRENT" ] && [ "$CURRENT" != "$TAG" ]; then
    log "되돌림: $CURRENT"
    switch_to "$CURRENT"
    if healthy; then log "되돌림 완료. 서비스는 $CURRENT 로 돌고 있습니다"; else log "되돌림도 실패했습니다. 직접 확인이 필요합니다"; fi
fi
exit 1
