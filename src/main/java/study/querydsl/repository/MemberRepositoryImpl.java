package study.querydsl.repository;

import com.querydsl.core.QueryResults;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.support.PageableExecutionUtils;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.dto.QMemberTeamDto;


import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import study.querydsl.entity.Member;

import static org.springframework.util.StringUtils.hasText;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

/**
 * 클래스명 중요
 * 1. JpaRepository 상속받은 Interface 의 이름 + Impl
 * ex) MemberRepository + Impl -> MemberRepositoryImpl
 * 2. 스프링 데이터 2.x부터는 사용자 정의 인터페이스 명 + Impl 방식도 지원
 * ex) MemberRepositoryCustom + Impl -> MemberRepositoryCustomImpl
 */
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

//    public MemberRepositoryImpl(EntityManager em) { // JPAQueryFactory 를 별도로 @Bean 으로 등록하지 않았을 경우
//        this.queryFactory = new JPAQueryFactory(em);
//    }

    public MemberRepositoryImpl(JPAQueryFactory jpaQueryFactory) { // JPAQueryFactory 를 별도로 @Bean 등록 했을 경우
        this.queryFactory = jpaQueryFactory;
    }

    @Override
    public List<MemberTeamDto> search(MemberSearchCondition condition) {
        return queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .fetch();
    }

    /**
     * 단순한 페이징, fetchResults() 사용
     */
    @Override
    public Page<MemberTeamDto> searchPageSimple(MemberSearchCondition condition, Pageable pageable) {
        QueryResults<MemberTeamDto> results = queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .offset(pageable.getOffset())   // 몇번재부터 조회
                .limit(pageable.getPageSize()) // 페이지당 몇 개 조회할 것인가
                .fetchResults();    // count 쿼리랑 content 쿼리 2개 날림

        // 총 아래 2개의 쿼리가 날아감
        List<MemberTeamDto> content = results.getResults();// 실제 데이터
        long total = results.getTotal(); // count 갯수

        return new PageImpl<>(content, pageable, total);
    }

//    /**
//     * 복잡한 페이징
//     * 데이터 조회 쿼리와, 전체 카운트 쿼리를 분리
//     */
//    @Override
//    public Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable) {
//
//        // content 쿼리
//        List<MemberTeamDto> content = queryFactory
//                .select(new QMemberTeamDto(
//                        member.id.as("memberId"),
//                        member.username,
//                        member.age,
//                        team.id.as("teamId"),
//                        team.name.as("teamName")))
//                .from(member)
//                .leftJoin(member.team, team)
//                .where(
//                        usernameEq(condition.getUsername()),
//                        teamNameEq(condition.getTeamName()),
//                        ageGoe(condition.getAgeGoe()),
//                        ageLoe(condition.getAgeLoe())
//                )
//                .offset(pageable.getOffset())
//                .limit(pageable.getPageSize())
//                .fetch();
//
//        // total 쿼리
//        long count = queryFactory
//                .select(member)
//                .from(member)
//                .leftJoin(member.team, team)
//                .where(
//                        usernameEq(condition.getUsername()),
//                        teamNameEq(condition.getTeamName()),
//                        ageGoe(condition.getAgeGoe()),
//                        ageLoe(condition.getAgeLoe())
//                )
//                .fetchCount();
//
//        return new PageImpl<>(content, pageable, count);
//    }

    /**
     * 복잡한 페이징(+countQuery 더 최적화)
     * 데이터 조회 쿼리와, 전체 카운트 쿼리를 분리
     *
     * count 쿼리가 생략 가능한 경우 생략해서 처리 (아래 특정 조건)
     * 1. 페이지 시작이면서 컨텐츠 사이즈가 페이지 사이즈보다 작을 때
     * ex) 페이지 시작(0)이면서 컨텐츠 사이즈(3)가 페이지 사이즈(10)보다 작다면 컨텐츠 사이즈(3)가 카운트 갯수
     *
     * 2. 마지막 페이지 && 컨텐츠 사이즈 < 페이지 사이즈 (offset + 컨텐츠 사이즈를 더해서 전체 사이즈 구함, 더 정확히는 마지막 페이지 이면서 컨텐츠 사이즈가 페이지 사이즈보다 작을 때)
     * ex) 예를 들어 전체 데이터는 103개이고, 페이지 사이즈는 10이라고 한다면 마지막 페이지 일때는 offset(100), 컨텐츠 사이즈(3)이라서 카운트는 103
     *
     */
    @Override
    public Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable) {

        // content 쿼리
        List<MemberTeamDto> content = queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // total 쿼리
        JPAQuery<Member> countQuery = queryFactory
                .select(member)
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                );

        // 원래 fetchCount() 를 실행해야 count 쿼리가 날아간다.
        // 특정 조건인 경우에만 count 쿼리가 날아간다.
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchCount);
    }

    private BooleanExpression usernameEq(String username) {
        return hasText(username) ? member.username.eq(username) : null;
    }

    private BooleanExpression teamNameEq(String teamName) {
        return hasText(teamName) ? team.name.eq(teamName) : null;
    }

    private BooleanExpression ageGoe(Integer ageGoe) {
        return ageGoe != null ? member.age.goe(ageGoe) : null;
    }

    private BooleanExpression ageLoe(Integer ageLoe) {
        return ageLoe != null ? member.age.loe(ageLoe) : null;
    }
}
