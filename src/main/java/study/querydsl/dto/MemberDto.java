package study.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemberDto {

    private String username;
    private int age;

    @QueryProjection   // @QueryProjection 붙인 후, gradle - compileQuerydsl 실행하면 Dto도 Q파일이 생성된다.
    public MemberDto(String username, int age) {
        this.username = username;
        this.age = age;
    }
}
