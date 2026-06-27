# 1. Purpose
### The Auth Service is responsible for:

- Authentication
- Authorization
- Identity
- Credentials

### The Auth Service is NOT responsible for:

- Customer Profile
- Bank Accounts
- Transactions
- KYC

# 2. Aggregate
 - User (Aggregate Root).

# 3. Value Objects

- UserId
- EmailAddress
- PasswordHash

# 4. Entities

- User
- Role
- Permission

# 5. User Lifecycle

PENDING_VERIFICATION

↓

ACTIVE

↓

LOCKED

↓

DISABLED

# 6. Domain Rules

This is where the important part starts.

Let's define them.

Identity Rules
#### A User:
- must have a UserId
- must have an EmailAddress
- must have a PasswordHash
- must have at least one Role
- must always have a Status

### Email Rules
- Email must be unique.
- Email comparison is case-insensitive.
- Email is normalized to lowercase.

### Password Rules
- Domain stores only hashes.
- Plain passwords never enter the domain.

### Role Rules
- Duplicate roles are forbidden.

### Authentication Rules
- LOCKED users cannot authenticate.
- DISABLED users cannot authenticate.
- PENDING_VERIFICATION users cannot authenticate.
- Only ACTIVE users may authenticate.

### Domain Events

- UserRegistered
- EmailVerified
- PasswordChanged
- UserLocked
- RoleAssigned
- RoleRevoked
