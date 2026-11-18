package be.bstorm.formation.mapper;

import be.bstorm.formation.dto.response.CurrentUserResponse;
import be.bstorm.formation.dto.response.TokenResponse;
import be.bstorm.formation.entity.User;
import be.bstorm.formation.framework.config.MapStructConfig;
import be.bstorm.formation.framework.security.CurrentUser;
import be.bstorm.formation.framework.security.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

/** Conversions de l'authentification (feature 001, § 5.6). */
@Mapper(config = MapStructConfig.class)
public interface AuthMapper {

    /** Appelé seulement si user.getRoles() est non vide (le service vérifie avant, RG-03 / RG-22). */
    CurrentUser toCurrentUser(User user);

    /** Rôles triés dans l'ordre de déclaration de l'enum ({@link #sortRoles(Set)}). */
    CurrentUserResponse toResponse(CurrentUser user);

    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "expiresIn", source = "expiresIn")
    @Mapping(target = "user", source = "user")
    TokenResponse toTokenResponse(String accessToken, long expiresIn, CurrentUser user);

    default List<Role> sortRoles(Set<Role> roles) {
        return roles.stream().sorted().toList();
    }
}
