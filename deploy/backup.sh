#!/usr/bin/env bash
# DB 를 매일 덤프한다. server-setup.sh 가 cron 에 등록한다 (새벽 4시).
#
# 서버 디스크에만 두면 서버가 날아갈 때 같이 날아간다. BACKUP_REMOTE 를 정하면 rclone 으로 서버 밖에도 복사한다.
#   예: BACKUP_REMOTE=oci:portfolio-backup  (rclone config 로 미리 만든 원격 저장소)
#
# 되살리기:
#   gunzip -c backups/portfolio-2026-10-02-0400.sql.gz | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" portfolio'
set -euo pipefail
cd "$(dirname "$0")"

KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
mkdir -p backups
out="backups/portfolio-$(date +%F-%H%M).sql.gz"

# --single-transaction: 테이블을 잠그지 않고 한 시점의 모습을 뜬다 (InnoDB). 앱을 멈출 필요가 없다
docker compose exec -T mysql sh -c \
    'exec mysqldump --single-transaction --no-tablespaces -uroot -p"$MYSQL_ROOT_PASSWORD" portfolio' \
    | gzip > "$out.tmp"

# 덤프가 중간에 죽으면 몇 바이트짜리 파일이 남는다. 그걸 "백업 성공"으로 착각하지 않게 끝줄을 확인한다
if ! gunzip -c "$out.tmp" | tail -n 1 | grep -q "Dump completed"; then
    rm -f "$out.tmp"
    echo "[$(date '+%F %T')] 백업 실패: 덤프가 끝까지 쓰이지 않았습니다" >&2
    exit 1
fi
mv "$out.tmp" "$out"
chmod 600 "$out"   # 잔고·토큰(암호문)이 들어 있다
echo "[$(date '+%F %T')] 백업: $out ($(du -h "$out" | cut -f1))"

find backups -name 'portfolio-*.sql.gz' -mtime +"$KEEP_DAYS" -delete

if [ -n "${BACKUP_REMOTE:-}" ]; then
    rclone copy "$out" "$BACKUP_REMOTE" && echo "[$(date '+%F %T')] 서버 밖 복사: $BACKUP_REMOTE"
fi
