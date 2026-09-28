package com.sakhtyar.identity.mapper;

import com.sakhtyar.auth.api.AuthDtos.MeResponse;
import com.sakhtyar.auth.api.UserAdminDtos.UserResponse;
import com.sakhtyar.identity.domain.Permission;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRole;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(target = "permissions", expression = "java(permissionNames(user.getRole()))")
    MeResponse toMeResponse(UserEntity user);

    @Mapping(target = "permissions", expression = "java(permissionNames(user.getRole()))")
    UserResponse toUserResponse(UserEntity user);

    default Set<String> permissionNames(UserRole role) {
        if (role == null) {
            return Set.of();
        }
        return role.permissions().stream()
                .map(Permission::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
