package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static com.querydsl.jpa.JPAExpressions.select;
import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        // member1을 찾아라.
        String qlString =
                "select m from Member m " +
                        "where m.username = :username";
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }


    // ----------------------------------------------기본문법-------------------------------------------------------
    @Test
    public void startQuerydsl() {
//        QMember m = new QMember("m");
//        QMember m = QMember.member;

        Member findMember = queryFactory.select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1")
                        , member.age.eq(10))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch() {
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchOne();

        Member fetchFirst = queryFactory
                .selectFrom(member)
                .fetchFirst();

        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal();
        List<Member> content = results.getResults();

        long l = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);

    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); // (10 + 20) / 2

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35); // (30 + 40) / 2
    }

    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team) // leftJoin, rightJoin 다 가능함
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인(양 테이블의 모든 것을 다 조인)
     * 회원의 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name)) // 회원의 이름이 팀 이름과 같은 회원 조회
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t ON t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.team_id = t.id AND t.name = 'teamA'
     * on 절은 조인하는 대상을 필터링 하는 것
     */
    @Test
    public void join_on_filtering() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))  // 이 부분은 where 절로 변경 못한다.
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void join_on_filtering2() throws Exception {
        List<Tuple> result1 = queryFactory
                .select(member, team)
                .from(member)
                .join(member.team, team)
                .on(team.name.eq("teamA"))
                .fetch();

        // 위와 결과가 같다. (영한쌤은 아래처럼 더 많이 쓴다고 함. on보다는 where 절이 보통 더 익숙하므로)
        List<Tuple> result2 = queryFactory
                .select(member, team)
                .from(member)
                .where(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result1) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name)) // on 조건에 맞지 않는 team은 null로 표시되면서 row 다 나옴
//                .join(team).on(member.username.eq(team.name)) // on 조건에 맞지 않는 row는 아예 안 나옴
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    /**
     * 페치 조인이 없는 경우
     */
    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded =
                emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam()); // 즉, Team이 가져와졌는지.(lazy loading)
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    /**
     * 페치 조인이 있는 경우
     */
    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin() // join이든 left join이든 뒤에 fetchJoin()만 붙여주면 됨
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded =
                emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam()); // 즉, Team이 가져와졌는지.(lazy loading)
        assertThat(loaded).as("페치 조인 미적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() throws Exception {

        // 서브 쿼리니까 위의 member와 alise가 겹치면 안되니 위에서 memberSub 이란 것을 별도로 만들었다.
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max())     // JPAExpressions 을 static import 한 것
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 나이가 평균 이상인 회원
     */
    @Test
    public void subQueryGoe() throws Exception {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 나이가 평균 이상인 회원
     */
    @Test
    public void subQueryIn() throws Exception {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    @Test
    public void selectSubQuery() throws Exception {

        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();


        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void basicCase() throws Exception {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase() throws Exception {
        List<String> result = queryFactory
                .select(new CaseBuilder()   // 좀 복잡한 CaseBuilder 일 경우
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void constant() throws Exception {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat() throws Exception {
        // 참고: stringValue() 는 쓸 일 많음. ENUM을 처리할 때 자주 사용됨

        // {username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue())) // member.age는 숫자이므로 concat에 바로 못 쓰고 stringValue()로 변경
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    // 중급문법, --------------------------------------프로젝션과 결과 반환-------------------------------------

    @Test
    public void simpleProjection() throws Exception {
//        List<String> result = queryFactory
//                .select(member.username) // 이 경우도 projection 대상이 1개인 것임
//                .from(member)
//                .fetch();

        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void tupleProjection() throws Exception {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }

    // -----------------------------------------Dto 반환하는 방법--------------------------------------------------

    /**
     * JPQL로 조회한 후, DTO 로 반환하는 방법
     * 순수 jpa에서 DTO를 조회할 때는 new 명령어를 사용해야함
     * DTO의 package이름을 다 적어줘야해서 지저분함
     * 생성자 방식만 지원함
     */
    @Test
    public void findDtoByJPQL() throws Exception {
        // 패키지명 다 적어야 하니 참 별로인 방법..
//        em.createQuery("select m from Member m", MemberDto.class) // Member와 MemberDto 타입 안 맞아서 에러남
//        em.createQuery("select m.username, m.age from Member m", MemberDto.class) // 이것 역시 타입 안 맞아서 에러남
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m"
                        , MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * queryDsl 로 Dto 조회하는 방법1
     * setter - 프로퍼티 접근
     * setter를 통해서 값 주입(프로퍼티명이 일치해야 한다)
     */
    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,   // MemberDto에 기본생성자 있어야 함
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * queryDsl 로 Dto 조회하는 방법2
     * 필드 직접 접근 (getter, setter 없어도 됨)
     * MemberDto의 필드명과 member의 필드명이 일치해야 한다.
     */
    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 별칭이 다른 경우
     */
    @Test
    public void findUserDto() {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        // 필드명 달라서 별칭으로 일치시켜줘야 함
                        member.username.as("name"), // 별칭을 안 넣어주면 필드명이 달라서 null로 들어간다
//                        ExpressionUtils.as(member.username,"name"), // 위와 동일 (위 방식이 더 가독성 좋음)

                        // sub쿼리이므로 별칭을 꼭 줘야하는 경우
                        ExpressionUtils.as(JPAExpressions
                            .select(memberSub.age.max())
                                .from(memberSub), "age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * queryDsl 로 Dto 조회하는 방법3
     * 생성자 방식
     * MembetDto의 필드의 타입과 member.username, member.age 타입이 각각 일치해야 한다.
     */
    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class, // 실제 파라미터 2개 들어가는 생성자가 호출이 된다.
                        member.username,
                        member.age)) // @QueryInjection 방식과는 다르게, 여기서는 요소가 더 들어가거나 올바르지 않은 파라미터가 들어가면 컴파일 에러가 나지 않는다. runtime 에러 발생
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * queryDsl 로 Dto 조회하는 방법4
     * @QueryProjection
     * @QueryProjection 붙인 후, gradle - compileQuerydsl 실행하면 Dto도 Q파일이 생성된다.
     * 장점) 매우 깔끔, 컴파일 에러 잡아줌
     * 단점) queryDsl 코드에 의존적, 여러 layer 에서 해당 Dto를 사용할 수도 있는데, queryDsl의 의존성이 퍼지게 된다.
     */
    @Test
    public void findDtoByQueryProjection() throws Exception {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age)) // 요소가 더 들어가면 컴파일 에러로 잡아준다. 해당하는 MemberDto의 생성자가 실제로 호출됨
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    // ---------------------------------------------동적쿼리 -------------------------------------------------

    /**
     * BooleanBuilder
     */
    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam,ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        BooleanBuilder builder = new BooleanBuilder();
//        BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond); // 만약 usernameCond 가 반드시 null이 아니라는 보장이 있다면 이렇게 초기값 세팅도 해도 된다.
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and((member.age.eq(ageCond)));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    /**
     * 동적 쿼리 매우 추천하는 방법
     */
    @Test
    public void dynamicQuery_WhereParam() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam,ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        // 아래 쿼리가 가독성 매우 높음.
        // usernameEq, ageEq 이런 메서드는 조립해서 재활용할 수 있다.
        return queryFactory
                .selectFrom(member)
//                .where(usernameEq(usernameCond), ageEq(ageCond))   // where절에 null이 들어오면 무시한다. 쉼표로 나열하면 and 조건
                .where(allEq(usernameCond, ageCond))   // where절에 null이 들어오면 무시한다. 쉼표로 나열하면 and 조건
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) { // 메서드끼리 조합하려면 리턴 타입을 BooleanExpression
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression allEq(String usernameCon, Integer ageCond) {
        // 물론 NPE 방지 validation 체크가 들어가야 한다.
        return usernameEq(usernameCon).and(ageEq(ageCond));
    }

    // ----------------------------벌크 연산(수정, 삭제 배치 쿼리)--------------------------------
    // 벌크 연산은 영속성 컨텍스트를 무시하고 바로 db에 반영. 그래서 기존에 영속성 컨텍스트에 올라가 있는 entity 들의 상태들과 달라진다.
    // 그래서 후에 바로 DB에서 조회를 해오면 after 데이터를 들고왔지만, 영속성 컨텍스트에 pk 에 해당하는 값이 그대로 있으면 가져온 DB값은 버린다.(repeatable read)
    // 그래서 항상 bulk 연산 후에는 flush, clear를 영속성 컨텍스트를 초기화해주자z1

    @Test
    public void bulkUpdate() throws Exception {

        // 쿼리 실행전-->
        // member1 = 10 -> DB member1
        // member2 = 20 -> DB member2
        // member3 = 30 -> DB member3
        // member4 = 40 -> DB member4

        long count = queryFactory   // count 는 변경된 row 수
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();

        // member1 = 10 -> DB 비회원
        // member2 = 20 -> DB 비회원
        // member3 = 30 -> DB member3
        // member4 = 40 -> DB member4

        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    @Test
    public void bulkAdd() throws Exception {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();
    }

    @Test
    public void bulkMinus() throws Exception {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(-1))
                .execute();
    }

    @Test
    public void bulkMultiply() throws Exception {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.multiply(2))
                .execute();
    }

    @Test
    public void bulkDelete() throws Exception {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    // -------------------------------------sql function 호출하기------------------------------------
    // SQL function은 JPA와 같이 Dialect에 등록된 내용만 호출할 수 있다.

    @Test
    public void sqlFunction() throws Exception {

        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(
//                        Expressions.stringTemplate("function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower())) // ansi 표준에 있는 것들은 방언이어도 기본적으로 다 제공한다.
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}
