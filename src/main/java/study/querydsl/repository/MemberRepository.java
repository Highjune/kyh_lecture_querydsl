package study.querydsl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import study.querydsl.entity.Member;

import java.util.List;

/**
 * 스프링 데이터 jpa
 * JpaRepository 뿐 아니라 queryDsl 을 위한 MemberRepositoryCustom 도 상속받기
 *
 */
public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom, QuerydslPredicateExecutor<Member> {

    // select m from Member m where m.username = ? JPQL 자동생성
    List<Member> findByUsername(String username);
}
