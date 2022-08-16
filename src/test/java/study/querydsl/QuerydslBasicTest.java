package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {
    @Autowired
    EntityManager em;

    /**
     * 필드로 제공해도 동시성 문제는 걱정하지 않아도 된다.
     * 동시성 문제는 JPAQueryFactory를 생성할 때 제공하는 EntityManager에 달려 있는데
     * 스프링 프레임워크는 여러 쓰레드에서 동시에 같은 EM에 접근해도,
     * 트랜잭션 마다 별도의 영속성 컨텍스트를 제공하기 때문에 동시성 문제는 걱정하지 않아도 된다.
     */
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before(){
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
    public void startJPQL(){
        // member1을 찾아라.
        String qlString = "select m from Member m" +
                " where m.username = :username";

        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl(){
        // 어떤 QMember인지 구분하는 별칭을 줘야 함, 하지만 이후엔 만들어진 것을 쓸것.
        // 같은 테이블을 조인할 경우에만 이런 식으로 선언해서 사용하자. 아니면 아래 예제에서 볼 수 있듯이 기본 인스턴스 활용
        //QMember m = new QMember("m");

        /**
         * 문자로 작성하는 JPQL과 달리 런타임 시점에서 오류를 잡는게 아니라
         * 컴파일 시점에 오류를 잡아주고 자바 코드로 쿼리를 작성할 수 있다.
         * 또한 파라미터 바인딩을 자동 처리해준다.
         */
        Member findMember = queryFactory
                .select(member) // 기본 인스턴스(QMember.member)를 static import와 함께 사용, 이 방법을 권장
                .from(member)
                .where(member.username.eq("member1")) // 파라미터 바인딩 처리리
               .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * 검색 조건 쿼리
     * 다음과 같이 JPQL이 제공하는 모든 검색 조건을 제공한다.
     * member.username.eq("member1") // username = 'member1'
     * member.username.ne("member1") //username != 'member1'
     * member.username.eq("member1").not() // username != 'member1'
     * member.username.isNotNull() //이름이 is not null
     * member.age.in(10, 20) // age in (10,20)
     * member.age.notIn(10, 20) // age not in (10, 20)
     * member.age.between(10,30) //between 10, 30
     * member.age.goe(30) // age >= 30
     * member.age.gt(30) // age > 30
     * member.age.loe(30) // age <= 30
     * member.age.lt(30) // age < 30
     * member.username.like("member%") //like 검색
     * member.username.contains("member") // like ‘%member%’ 검색
     * member.username.startsWith("member") //like ‘member%’ 검색
     */
    @Test
    public void search(){

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1").and(member.age.eq(10)))
                //.where(member.username.eq("member1"),(member.age.eq(10))) // 파라미터로 검색조건을 추가하면 AND 조건이 추가된다.
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * - fetch(): 리스트 조회, 데이터 없으면 빈 리스트 반환
     *
     * - fetchOne(): 단 건 조회
     *  - 결과가 없으면: null
     *  - 결과가 둘 이상이면: com.querydsl.core.NonUniqueResultException
     *
     * - fetchFirst(): limit(1).fetchOne()
     *
     * - fetchResults 와 fetchCount는 deprecated 됐는데 복잡한 쿼리(다중그룹 쿼리)에선 count 쿼리가 잘 동작하지 않기 때문
     *  - 따라서 강의에서 여러번 들은 것처럼 count 쿼리는 따로 짜는 게 좋을 듯 하다.
     */
    @Test
    public void resultFetch(){
        // List
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        // 단 건
        Member fetchOne = queryFactory
                .selectFrom(QMember.member)
                .fetchOne();

        // 처음 한 건 조회
       Member fetchFirst = queryFactory
                .selectFrom(QMember.member)
                .fetchFirst();
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last), nullsFirst: null이면 첫번째에 출력
     */
    @Test
    public void sort(){
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

    /**
     * jpql과 유사하게 offset과 limit을 활용하여 페이징
     */
    @Test
    public void paging(){ // count 쿼리는 조인이 필요 없는 경우 따로 짜는 게 좋다.
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void aggregation(){
        /**
         * JPQL이 제공하는 모든 집합 함수를 제공한다.
         * queryDsl의 Tuple 형태로 들고 옴.
         */
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
     * 팀의 이름과 각 팀의 평균 연령을 구해라
     */
    @Test
    public void group() throws Exception{
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name) // 그룹화된 결과를 제한하려면 having을 사용하면 된다.(jpql과 유사)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);

    }

    /**
     * [기본 조인]
     * - 조인의 기본 문법은 첫 번째 파라미터에 조인 대상을 지정하고, 두 번째 파라미터에 별칭으로 사용할 Q 타입을 지정하면 된다.
     *
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join(){
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team) // 외부(left, right) 조인도 가능하다.
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * [세타 조인]
     * - from 절에 여러 엔티티를 선택해서 세타 조인
     * - 조인 on절을 사용하면 외부 조인 가능
     *
     * 회원의 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 1. 조인 대상 필터링
     *
     * 예) 회원고 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.TEAM_ID=t.id and t.name='teamA'
     *
     * 참고: on절을 활용해 조인 대상을 필터링 할 때, 외부 조인이 아니라 내부 조인을 사용하면
     * where 절에서 필터링 하는 것과 기능일 동일하다.
     *
     * 따라서 on절을 활용한 조인 대상 필터링을 사용할 때
     * 내부조인이면 where절로 해결하고, 정말 외부조인이 필요한 경우에만 이 기능을 사용하자
    */
    @Test
    public void join_on_filtering(){
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for(Tuple tuple: result){
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 2. 연관관계 없는 엔티티 외부 조인
     * 예) 회원 이름이 팀 이름과 같은 대상 외부 조인
     * JPQL: SELECT m, t FROM Member m LEFT JOIN Team t on m.username = t.name
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.username = t.name
     *
     * 주의:
     * 문법을 잘봐야 한다. leftJoin 부분에 일반 조인과 다르게 엔티티 하나만 들어간다.
     * 일반 조인: leftJoin(member.team, team)
     * on 조인: from(member).leftJoin(team).on(~~)
     */
    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for(Tuple tuple: result){
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo(){
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(QMember.member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    /**
     * [페치 조인]
     *
     * 사용 방법: join(), leftJoin() 등 조인 기능 뒤에 fetchJoin()이라고 추가하면 된다.
     */
    @Test
    public void fetchJoin(){
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(QMember.member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery(){
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 나이가 평균 이상인 회원 조회
     */
    @Test
    public void subQueryGoe(){
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 서브쿼리 여러 건 처리, in 사용
     */
    @Test
    public void subQueryIn(){
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    /**
     * select 절에 subquery
     */
    @Test
    public void subQueryInSelect(){
        QMember memberSub = new QMember("memberSub");

        List<Tuple> fetch = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ).from(member)
                .fetch();

        for (Tuple tuple: fetch){
            System.out.println("username = " + tuple.get(member.username));
        }
    }


    /**
     * [case문]
     * case문을 사용하여 효율적으로 처리할 상황이 있다.
     * 하지만 아래 예제와 같은 경우는 애플리케이션 로직에서 수행하는 것이 알맞다.
     */
    @Test
    public void basicAndComplexCase(){
        //basic case
        List<String> fetch = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for(String string: fetch){
            System.out.println("string = " + string);
        }

        //complex case
        List<String> fetch2 = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0,20)).then("0~20살")
                        .when(member.age.between(21,30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for(String string: fetch2){
            System.out.println("string = " + string);
        }
    }

    /**
     * orderBy에서 Case문 함께 사용하기
     *
     * 예) 다음과 같은 임의의 순서로 회원을 출력
     * 1. 0~30살이 아닌 회원을 가장 먼저 출력
     * 2. 0~20살 회원 출력
     * 3. 21~30살 회원 출력
     *
     * Querydsl은 자바 코드로 작성하기 때문에 rnakPath처럼 복잡한 고전을 변수로 선언해서 select절, orderBy절에서 함께 사용할 수 있다.
     */
    @Test
    public void orderByWithCase(){
        NumberExpression<Integer> rankPath = new CaseBuilder()
                .when(member.age.between(0,20)).then(2)
                .when(member.age.between(0,20)).then(1)
                .otherwise(3);

        List<Tuple> result = queryFactory
                .select(member.username, member.age, rankPath)
                .from(member)
                .orderBy(rankPath.desc())
                .fetch();

        for (Tuple tuple:result){
            System.out.println("username = " + tuple.get(member.username) +
                    " age = " + tuple.get(member.age) + " rank = " + tuple.get(rankPath));
        }
    }

    /**
     * 상수가 필요하면 Expressions.constant(~~) 사용
     */
    @Test
    public void constant(){
        List<Tuple> fetch = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for(Tuple tuple: fetch){
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 문자 더하기
     * 참고: 문자가 아닌 다른 타입들은 stringValue()로 문자로 변환 가능. 이 방법은 ENUM을 처리할 때도 자주 사용한다.
     */
    @Test
    public void concat(){
        //{username}_{age}
        List<String> fetch = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for(String string: fetch){
            System.out.println("string = " + string);
        }
    }


    /**
     * [프로젝션과 결과 반환 - 기본]
     * 프로젝션: select 대상 지정
     *
     * 1. 프로젝션 대상이 하나
     * - 프로젝션 대상이 하나면 타입을 명확하게 지정할 수 있음
     *
     * 2. 튜플 조회
     * - 프로젝션 대상이 둘 이상일 때 사용
     * - 프로젝션 대상이 둘 이상이면 튜플이나 DTO로 조회
     *
     * 참고: 튜플 타입의 경우 queryDsl에 종속적이니 리포지토리 안에서만 쓰는 것을 권장하고
     *      애플리케이션 단 등 외부로 데이터를 내보낼 땐 DTO를 사용하자.
     */
    @Test
    public void simpleProjection(){
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for(String username:result){
            System.out.println("username = " + username);
        }
    }

    @Test
    public void tupleProjection(){
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for(Tuple tuple: result){
            System.out.println("username = " + tuple.get(member.username));
            System.out.println("age = " + tuple.get(member.age));
        }
    }


    /**
     * 순수 JPA에서 DTO 조회 코드
     * - 순수 JPA에서 DTO를 조회할 땐 new 명령어를 사용해야 함
     * - DTO의 package 이름을 다 적어줘야 해서 지저분함
     * - 생성자 방식만 지원함
     */
    @Test
    public void findDtoByJPQL(){
        List<Member> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username,m.age) from Member m", Member.class)
                .getResultList();

        result.forEach(memberDto -> {
            System.out.println("memberDto = " + memberDto);
        });
    }

    /**
     * Querydsl을 사용한 DTO 조회 코드
     * 1. 프로퍼티 접근 - Setter 사용
     * 2. 필드 직접 접근
     * 3. 생성자 사용
     */
    @Test
    public void findDtoBySetter(){
        // 대상 클래스(MemberDto)에 기본 생성자가 존재해야 오류가 발생하지 않는다.
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByField(){
        // 대상 클래스(MemberDto)에 기본 생성자가 존재해야 함.
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

    @Test
    public void findDtoByConstructor(){
        // 대상 클래스(MemberDto)에 기본 생성자가 존재해야 함.
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 엔티티와 DTO 간 별칭이 다를 때
     */
    @Test
    public void findUserDto(){
        // 필드에 별칭 적용 -> member.username.as("name")
        List<UserDto> result = queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username.as("name"),
                        member.age))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }

        // 필드나 서브 쿼리에 별칭 적용 -> ExpressionUtils.as({source}, {alias})
        // 조금 억지스러운 예제긴 하지만 이 기능을 보여주기 위함이다.
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result2 = queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username.as("name"),
                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub), "age")))
                .from(member)
                .fetch();
    }


    /**
     * [생성자 + @QueryProjection 을 사용한 DTO 조회]
     *
     * 사용 방법
     * 1. 대상 클래스의 생성자에 @QueryProjection 어노테이션을 추가한다.
     * 2. gradle에서 compileQuerydsl을 실행해 Q타입 클래스를 생성한 후 아래와 같이 사용한다.
     *
     * 장점
     * - 앞서 확인한 Querydsl Projections.constructor를 이용한 Dto 조회는 런타임에 오류를 잡을 수 있지만
     *   이 방식의 경우 컴파일 타임에 오류를 잡을 수 있다
     *
     * 단점
     * - 다만 DTO에 QueryDSL 어노테이션을 유지해야 하는 점(DTO가 Querydsl에 종속된다)과
     *   DTO까지 Q파일을 생성해야 하는 단점이 있다.
     */
    @Test
    public void findDtoByQueryProjection(){
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 동적 쿼리 - BooleanBuilder 사용
     */
    @Test
    public void dynamicQuery_BooleanBuilder(){
        String usernameParam = "member1";
        Integer ageParam = 10;
        
        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
        
    }

    private List<Member> searchMember1(String usernameParam, Integer ageParam) {
        // 특정 파라미터를 필수로 주고 싶다면 아래와 같이 초깃값을 넣어도 된다
        //BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameParam));
        BooleanBuilder builder = new BooleanBuilder();
        if(usernameParam != null){
            builder.and(member.username.eq(usernameParam));
        }
        if(usernameParam != null){
            builder.and(member.username.eq(usernameParam));
        }
        return queryFactory
                .select(member)
                .where()
                .fetch();
    }
}
