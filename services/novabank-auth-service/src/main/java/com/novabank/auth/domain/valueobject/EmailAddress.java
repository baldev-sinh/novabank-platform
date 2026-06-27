    package com.novabank.auth.domain.valueobject;

    import java.util.Locale;
    import java.util.Objects;
    import java.util.regex.Pattern;

    public record EmailAddress(String value) {

        private static final Pattern VALID_EMAIL_PATTERN  = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

        public EmailAddress {
            Objects.requireNonNull(value, "value cannot be null");

            value = value.trim().toLowerCase(Locale.ROOT);

            if (value.isBlank()) {
                throw new IllegalArgumentException("Email address cannot be blank");
            }

            if (!VALID_EMAIL_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException("Email address format is invalid");
            }
        }

        public static EmailAddress of(String value) {
            return new EmailAddress(value);
        }

        @Override
        public String toString() {
            return value;
        }
    }
