package study.querydsl.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.dto.QMemberTeamDto;
import study.querydsl.entity.Member;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static org.springframework.util.StringUtils.hasText;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

/**
 * 순수한 jpa와 queryDsl
 */
@Repository
public class MemberJpqRepository {

    private final EntityManager em;
    private final JPAQueryFactory queryFactory;

    // queryFactory 직접 생성(Bean 등록을 별도로 하지 않은 상태)
    public MemberJpqRepository(EntityManager em) {
        this.em = em;
        this.queryFactory = new JPAQueryFactory(em);
    }

    // JPAQueryFactory 를 별도로 @Bean 등록한 상태 (이렇게 하면 @RequiredArgsConstructor 사용해서 편하게 할 수 있다)
//    public MemberJpqRepository(EntityManager em, JPAQueryFactory queryFactory) {
//        this.em = em;
//        this.queryFactory = queryFactory;
//    }

    public void save(Member member) {
        em.persist(member);
    }

    public Optional<Member> findById(Long id) {
        Member findMember = em.find(Member.class, id);
        return Optional.ofNullable(findMember);
    }

    public List<Member> findAll() {
        return em.createQuery("select m from Member m", Member.class)
                .getResultList();
    }

    public List<Member> findAll_Querydsl() {
        return queryFactory
                .selectFrom(member)
                .fetch();
    }

    public List<Member> findByUserName(String userName) {
        return em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", userName)
                .getResultList();
    }

    public List<Member> findByUserName_Querydsl(String userName) {
        return queryFactory
                .selectFrom(member)
                .where(member.username.eq(userName))
                .fetch();
    }

    /**
     * 동적 쿼리 방법 1
     * builder로 동적 쿼리 만들고 && Dto 로 한번에 조회하는 성능 최적화까지
     * 단점) BooleanBuilder 의 조건들이 다 들어가야 해서 가독성 떨어지서 번거롭다.
     */
    public List<MemberTeamDto> searchByBuilder(MemberSearchCondition condition) {
        BooleanBuilder builder = new BooleanBuilder();
        if (hasText(condition.getUsername())) {
            builder.and(member.username.eq(condition.getUsername()));
        }
        if (hasText(condition.getTeamName())) {
            builder.and(team.name.eq(condition.getTeamName()));
        }
        if (condition.getAgeGoe() != null) {
            builder.and(member.age.goe(condition.getAgeGoe()));
        }
        if (condition.getAgeLoe() != null) {
            builder.and(member.age.loe(condition.getAgeLoe()));
        }

        return queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(builder)
                .fetch();
    }

    /**
     * 동적 쿼리 방법 2
     * BooleanExpression 으로 where 안에 메서드 조합
     * 장점) 바로 쿼리 들어가므로 가독성도 높다.
     * select 의 projection이 달라져도 코드를 재사용할 수 있다. 즉 select 문 안에 select(member)로 member entity 로 조회변경하더라도 다른 코드 변경X
     */
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

    // 각 메서드 안에서 null 체크만 제대로 해준다면 아래처럼 메서드들을 서로 조합 가능
    public BooleanExpression ageBetween(int ageLoe, int ageGoe) {
        return ageGoe(ageLoe).and(ageGoe(ageGoe));
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
