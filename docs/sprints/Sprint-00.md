## NVB-002 - Parent Dependency Management

**Status:** Completed

### Key Decisions
- Spring Boot BOM imported using dependencyManagement.
- Parent POM manages versions only.
- Child services declare only the dependencies they use.

### Lessons Learned
- dependencyManagement does not add dependencies.
- Spring Boot BOM should manage Spring ecosystem versions.
- Keep the parent POM focused on engineering concerns.