package vn.edu.fpt.savora.mapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.fpt.savora.entity.User;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
@Mapper(componentModel = "spring")
public interface AuthUserMapper {
    @Mapping(
            target = "fullName",
            expression = "java((user.getFirstName() + \" \" + user.getLastName()).trim())"
    )
    AuthUserResponse toResponse(User user);
}
