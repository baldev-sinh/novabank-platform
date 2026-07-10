package com.novabank.auth.infrastructure.persistence.user.adapter;


import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import com.novabank.auth.infrastructure.persistence.user.entity.UserEntity;
import com.novabank.auth.infrastructure.persistence.user.repository.SpringDataUserRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springDataRepository;

    @Override
    public User save(User user) {
        Objects.requireNonNull(user, "User cannot be null");
        UserEntity entity = mapToEntity(user);
        UserEntity savedEntity = springDataRepository.save(entity);
        return mapToDomain(savedEntity);
    }

    @Override
    public Optional<User> findById(UserId id) {
        Objects.requireNonNull(id, "UserId cannot be null");
        return springDataRepository.findById(id.value())
            .map(this::mapToDomain);
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        Objects.requireNonNull(email, "Email cannot be null");
        return springDataRepository.findByEmail(email.value())
            .map(this::mapToDomain);
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        Objects.requireNonNull(email, "Email cannot be null");
        return springDataRepository.existsByEmail(email.value());
    }

    private UserEntity mapToEntity(User user){
        Objects.requireNonNull(user, "User cannot be null");
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

    private User mapToDomain(UserEntity entity){
        Objects.requireNonNull(entity, "UserEntity cannot be null");
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
