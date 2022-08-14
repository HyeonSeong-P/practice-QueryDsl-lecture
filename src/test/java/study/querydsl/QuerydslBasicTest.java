package study.querydsl;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;

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
}
