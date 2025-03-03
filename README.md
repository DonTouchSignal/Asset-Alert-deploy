# MSA 기반 시스템 - 종목 및 알림 서비스
## 프로젝트 소개
이 프로젝트는 마이크로서비스 아키텍처(MSA)를 기반으로 개발된 '돈터치 시그널' 애플리케이션의 종목 및 알림 관련 서비스입니다. 사용자가 주식 및 가상화폐 종목의 실시간 시세를 조회하고, 관심 종목을 등록하며, 목표 가격 알림을 설정할 수 있는 기능을 제공합니다. Spring Boot, Redis, Kafka를 활용하여 확장 가능하고 실시간 데이터 처리가 가능한 시스템을 구축했습니다.

## 주요 구성 요소
### 종목 서비스
* **역할**: 주식 및 가상화폐 종목 검색, 상세 조회, 관심 종목 관리
* **포트**: 8082
* **기술**: Spring Boot, JPA, Redis, WebSocket

### 알림 서비스
* **역할**: 가격 변동 알림, 목표 가격 알림, 알림 내역 관리
* **포트**: 8083
* **주요 기능**: Kafka 이벤트 처리, WebSocket을 통한 실시간 알림

## 기술 스택
* **프레임워크**: Spring Boot, Spring WebSocket
* **데이터 처리**: Kafka, Redis
* **데이터베이스**: JPA, MySQL
* **외부 API 연동**: 한국투자증권 API, Upbit API
* **빌드/배포**: Docker, GitHub Actions
* **인프라**: AWS EC2

## 구현 특징
1. **실시간 데이터 처리**
   * Kafka를 활용한 이벤트 기반 아키텍처
   * WebSocket을 통한 실시간 시세 및 알림 제공
   * Redis 캐싱을 통한 성능 최적화

2. **외부 API 통합**
   * 한국투자증권 API를 통한 국내/해외 주식 데이터 수집
   * Upbit API를 통한 가상화폐 데이터 수집
   * Redis를 활용한 API 토큰 및 데이터 캐싱

3. **맞춤형 알림 시스템**
   * 목표 가격 도달 시 이벤트 발생 및 알림
   * 가격 변동률에 따른 알림 트리거
   * WebSocket을 통한 실시간 알림 전송

## 아키텍처 다이어그램

```
클라이언트 → API Gateway(8080) → 종목 서비스(8082)
                    |            → 알림 서비스(8083)
                    ↓
             Eureka Server(8761)
                    ↓
             서비스 디스커버리
```

## API 명세
### 종목 API
* **검색**: `GET /asset/search?keyword={keyword}`
* **상세 조회**: `GET /asset/{symbol}`
* **변동률 상위 조회**: `GET /asset/top-movers`
* **관심 종목 관리**: 
  * `POST /asset/favorite?symbol={symbol}`
  * `DELETE /asset/favorite?symbol={symbol}`
  * `GET /asset/favorite`
* **목표 가격 관리**:
  * `POST /asset/target-price?symbol={symbol}&targetPrice={price}&condition={ABOVE/BELOW}`
  * `GET /asset/target-prices`
  * `DELETE /asset/target-price?symbol={symbol}`

### 알림 API
* **알림 내역 조회**: `GET /notifications`
* **알림 삭제**: 
  * `DELETE /notifications/{notificationId}`
  * `DELETE /notifications` (모든 알림)

## WebSocket 엔드포인트
* **종목 시세**: `/ws/stock-prices`
* **알림**: `/ws/notifications?email={userEmail}`

## 배포 환경
AWS EC2 인스턴스에 Docker Compose를 통해 배포되며, GitHub Actions 워크플로우를 통한 자동 배포가 구성되어 있습니다. 종목 및 알림 서비스는 각각 독립적인 컨테이너로 배포되며, Eureka 서버에 등록되어 관리됩니다.

## 학습 포인트
* 실시간 데이터 처리를 위한 이벤트 기반 아키텍처 구현
* Redis를 활용한 데이터 캐싱 및 성능 최적화
* WebSocket을 통한 양방향 실시간 통신 구현
* 외부 API 연동 및 토큰 관리 메커니즘
* MSA 환경에서의 서비스 간 통신 패턴 적용
