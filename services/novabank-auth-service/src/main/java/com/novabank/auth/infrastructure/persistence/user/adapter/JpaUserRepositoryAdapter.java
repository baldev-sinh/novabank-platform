package com.novabank.auth.infrastructure.persistence.user.adapter;


import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import com.novabank.auth.infrastructure.persistence.user.entity.UserEntity;
import com.novabank.auth.infrastructure.persistence.user.repository.SpringDataUserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springDataRepository;

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        UserEntity savedEntity = springDataRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return Optional.empty();
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return false;
    }

    private UserEntity toEntity(User user){
        return UserEntity.from(
            user.id().value(),
            user.email().value(),
            user.passwordHash().value(),
            user.status(),
            user.roles(),
            user.createdAt(),
            user.updatedAt()
        );
    }

    private User toDomain(UserEntity entity){

        return  User.restore(
            UserId.of(entity.getId()),
            EmailAddress.of(entity.getEmail()),
            PasswordHash.of(entity.getPasswordHash()),
            entity.getStatus(),
            entity.getRoles(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

}
