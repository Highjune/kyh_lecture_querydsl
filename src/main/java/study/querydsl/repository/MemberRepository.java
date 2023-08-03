package study.querydsl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.querydsl.entity.Member;

import java.util.List;

/**
 * 스프링 데이터 jpa
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    // select m from Member m where m.username = ? JPQL 자동생성
    List<Member> findByUsername(String username);
}
