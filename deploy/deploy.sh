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

# ── 띄우기 전 확인 ──
# 운영에서 빠지면 조용히 위험해지는 값들. 에러가 안 나고 "열린 채로" 돈다
preflight() {
    [ -f .env ] || { log ".env 가 없습니다. env.example 을 복사해 채우세요"; exit 1; }
    local missing=()
    for k in DOMAIN COMPOSE_DB_PASSWORD COMPOSE_DB_ROOT_PASSWORD OWNER_PASSWORD TOKEN_ENCRYPTION_KEY; do
        grep -qE "^${k}=.+" .env || missing+=("$k")
    done
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
# 처음 배포면 DB·Caddy 도 같이 띄운다. 이미 떠 있으면 아무것도 안 한다
if [ -z "$CURRENT" ]; then
    docker tag "$REPO:$TAG" stock-portfolio:current
    docker compose up -d
else
    # compose.yml 에서 DB·Caddy 설정이 바뀌었으면 반영한다 (안 바뀌었으면 아무것도 안 한다)
    docker compose up -d --no-recreate mysql >/dev/null
    docker compose up -d --no-deps caddy >/dev/null
    # Caddyfile 은 파일만 바뀌어서 compose 가 모른다. 끊김 없는 reload 로 매번 다시 읽힌다
    docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null
    switch_to "$TAG"
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
