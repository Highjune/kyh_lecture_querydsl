## queryDsl 설정 파일
- https://devfunny.tistory.com/844 참고
- 강의에서의 java와 springboot 버전과는 다르게 여기서는 `java11, springboot 2.7.*` 버전으로 설정했다.
- 처음에 설정하는 것에 좀 고생을 했기에 기록.

```
buildscript {
	ext {
		queryDslVersion = "5.0.0"
	}
}

plugins {
	id 'org.springframework.boot' version '2.7.2'
	id 'io.spring.dependency-management' version '1.0.12.RELEASE'
	//querydsl 추가
	id "com.ewerk.gradle.plugins.querydsl" version "1.0.10"
	id 'java'
}

group = 'study'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '11'

configurations {
	compileOnly {
		extendsFrom annotationProcessor
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-web'

	//querydsl 추가
	implementation "com.querydsl:querydsl-jpa:${queryDslVersion}" // querydsl 라이브러리
	annotationProcessor "com.querydsl:querydsl-apt:${queryDslVersion}" //  Querydsl 관련 코드 생성 기능 제공

	compileOnly 'org.projectlombok:lombok'
	developmentOnly 'org.springframework.boot:spring-boot-devtools'
	runtimeOnly 'com.h2database:h2'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation('org.springframework.boot:spring-boot-starter-test') {
		exclude group: 'org.junit.vintage', module: 'junit-vintage-engine'
	}
}

tasks.named('test') {
	useJUnitPlatform()
}

//querydsl 추가 시작 (위에 plugin 추가 부분과 맞물림)
def querydslDir = "$buildDir/generated/querydsl"
querydsl {
	jpa = true
	querydslSourcesDir = querydslDir
}

sourceSets { // IDE의 소스 폴더에 자동으로 넣어준다.
	main.java.srcDir querydslDir
}

configurations {
	querydsl.extendsFrom compileClasspath // 컴파일이 될때 같이 수행
}

compileQuerydsl {
	options.annotationProcessorPath = configurations.querydsl // Q파일을 생성해준다.
}
//querydsl 추가 끝
```