# MSA 기반 자산 모니터링 및 알림 시스템

## 프로젝트 소개

이 프로젝트는 마이크로서비스 아키텍처(MSA)를 기반으로 개발된 시스템으로, 자산 모니터링(Asset Service)과 알림 관리(Alert Service) 두 개의 핵심 서비스로 구성되어 있습니다. Spring Boot와 Spring Cloud 기술 스택을 활용하여 개발되었으며, Docker 컨테이너화 및 GitHub Actions를 통한 CI/CD 파이프라인을 구축하여 실시간 시장 데이터 모니터링과 사용자 맞춤형 가격 알림 서비스를 제공합니다.

## 주요 서비스 구성

### Asset Service (msa-asset)
- **역할**: 자산 시세 모니터링, 목표가격 설정, 시장 데이터 관리
- **포트**: 8082
- **주요 기능**:
  - 국내/해외 주식 및 암호화폐 실시간 시세 조회
  - WebSocket을 통한 실시간 시장 데이터 수신
  - 사용자별 관심 종목 및 목표가격 설정
  - 시장 데이터 변동 시 Kafka를 통한 이벤트 발행
  - KIS, Upbit 등 외부 API 연동

### Alert Service (msa-sb-alert)
- **역할**: 가격 알림 관리, 사용자 알림 전송
- **포트**: 8083
- **주요 기능**:
  - Kafka를 통한 목표 가격 이벤트 수신
  - WebSocket을 통한 사용자 실시간 알림 전송
  - 가격 알림 히스토리 관리 및 조회
  - Redis를 활용한 알림 상태 관리

## 기술 스택

- **백엔드**: Spring Boot 3.4.2, Java 17
- **보안**: Spring Security, CORS 관리
- **데이터베이스**: JPA, Redis
- **메시징**: Kafka
- **실시간 통신**: WebSocket
- **외부 API**: KIS 증권 API, Upbit API
- **빌드/배포**: Docker, Docker Compose, GitHub Actions
- **인프라**: AWS EC2

## 주요 구현 사항

1. **실시간 시장 데이터 처리**
   - WebSocket을 활용한 증권사 API 실시간 데이터 수신
   - 사용자 설정 목표가격과 실시간 비교 및 알림 트리거
   - 대용량 데이터 처리를 위한 배치 처리 구현

2. **가격 알림 시스템**
   - Kafka를 통한 서비스 간 이벤트 기반 통신
   - WebSocket을 통한 클라이언트 실시간 알림 전송
   - 사용자별 알림 히스토리 관리

3. **외부 API 통합**
   - KIS 증권 API를 통한 국내/해외 주식 데이터 조회
   - Upbit API를 통한 암호화폐 시세 정보 조회
   - Redis를 활용한 API 토큰 캐싱 및 관리

4. **자동화된 CI/CD 파이프라인**
   - GitHub Actions를 통한 빌드 및 배포 자동화
   - Docker Compose를 통한 서비스 관리

## 아키텍처 다이어그램

```
클라이언트 → WebSocket 연결 ← → 마이크로서비스
                    ↓
           ┌───────────┴───────────┐
           ↓                       ↓
    Asset Service(8082)     Alert Service(8083)
           ↓                       ↓
    외부 API 연동                 Kafka 구독
    (KIS, Upbit)                    ↓
           ↓                     알림 처리
    Kafka 이벤트 발행               ↓
                              Redis/Database
```

## 배포 환경

AWS EC2 인스턴스에 Docker Compose를 통해 배포되며, GitHub Actions 워크플로우를 통한 자동 배포가 구성되어 있습니다. 코드 변경 시 main 브랜치에 병합되면 자동으로 빌드 및 배포가 진행됩니다.

## 주요 API 기능

### Asset Service
- 주식/암호화폐 시세 조회
- 목표 가격 설정 및 관리
- 관심 종목 등록/삭제
- 시장 데이터 조회

### Alert Service
- 가격 알림 조회
- 알림 히스토리 관리
- 알림 삭제
- WebSocket 연결을 통한 실시간 알림 수신

## 데이터 흐름

1. Asset Service는 외부 API와 WebSocket을 통해 실시간 시장 데이터를 수집합니다.
2. 사용자가 설정한 목표 가격에 도달하면 Kafka를 통해 이벤트를 발행합니다.
3. Alert Service는 Kafka에서 이벤트를 소비하여 처리합니다.
4. 처리된 알림은 WebSocket을 통해 클라이언트에게 실시간으로 전송됩니다.
5. 알림 히스토리는 데이터베이스에 저장되어 추후 조회가 가능합니다.

## 개발자 참고사항

- 각 서비스는 독립적으로 빌드 및 배포 가능
- 서비스 간 통신은 Kafka 메시징을 통해 이루어짐
- KIS 증권 API 및 Upbit API 키는 환경 변수로 안전하게 관리 필요
- Redis 연결 정보 및 기타 민감 정보는 별도 관리 필요
- WebSocket 연결 장애 시 자동 재연결 처리 로직 구현됨

## 시스템 운영

시스템은 Docker Compose를 통해 관리되며, 각 서비스는 독립적인 컨테이너로 실행됩니다. 서비스 로그는 Docker logs 명령을 통해 확인할 수 있으며, 배포 과정에서 자동으로 수집됩니다. GitHub Actions 워크플로우는 코드 변경 시 자동으로 CI/CD 파이프라인을 실행하여 최신 버전을 배포합니다.


## 상세 API명세(notion)
https://cooked-hockey-a64.notion.site/DonTouchSignal-1abdc215b6d580fe81d9ff09ffedc68c?pvs=4



