#!/usr/bin/env bash
# 새 우분투 서버(오라클 클라우드 Ampere, 22.04/24.04)를 한 번 준비한다. 여러 번 돌려도 된다.
#   scp -r deploy ubuntu@서버IP:~/stock-portfolio && ssh ubuntu@서버IP 'bash ~/stock-portfolio/server-setup.sh'
set -euo pipefail

log() { echo "== $*"; }

log "시간대: 한국"
sudo timedatectl set-timezone Asia/Seoul

log "도커"
if ! command -v docker >/dev/null; then
    curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"

# 오라클 우분투 이미지는 iptables 에 22번 말고 전부 막는 규칙이 미리 들어 있다.
# 클라우드 콘솔의 보안 목록(Security List)에서 80·443 을 열어도 여기서 또 막혀서 "연결 시간 초과" 가 난다
log "방화벽: 80·443 열기"
for port in 80 443; do
    if ! sudo iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null; then
        sudo iptables -I INPUT 5 -p tcp --dport "$port" -j ACCEPT
    fi
done
if ! sudo iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null; then
    sudo iptables -I INPUT 5 -p udp --dport 443 -j ACCEPT   # HTTP/3
fi
if command -v netfilter-persistent >/dev/null; then sudo netfilter-persistent save; fi

# 메모리가 작은 서버에서 MySQL·자바 빌드 캐시가 겹치면 OOM 으로 컨테이너가 죽는다. 스왑을 2GB 둔다
if ! swapon --show | grep -q /swapfile; then
    log "스왑 2GB"
    sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
    sudo mkswap /swapfile && sudo swapon /swapfile
    grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

DIR="$HOME/stock-portfolio"
mkdir -p "$DIR/backups"
chmod +x "$DIR"/*.sh 2>/dev/null || true

log "매일 04:00 DB 백업 (cron)"
line="0 4 * * * $DIR/backup.sh >> $DIR/backups/backup.log 2>&1"
( crontab -l 2>/dev/null | grep -v 'stock-portfolio/backup.sh' ; echo "$line" ) | crontab -

log "끝. 남은 일:"
echo "  1. 다시 로그인 (docker 그룹 반영)"
echo "  2. cd $DIR && cp env.example .env && chmod 600 .env && nano .env"
echo "  3. 첫 배포: ./deploy.sh   (이후는 GitHub Actions 가 합칠 때마다)"
echo "  4. 이 서버의 공인 IP 를 토스 허용 IP 에 등록"
