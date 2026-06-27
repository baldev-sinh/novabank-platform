    package com.novabank.auth.domain.valueobject;


    import java.util.Objects;

    public record PasswordHash(String value) {

        public PasswordHash{
            Objects.requireNonNull(value, "value cannot be null");

            value = value.trim();

            if(value.isBlank()){
                throw new IllegalArgumentException("Password hash cannot be blank");
            }
        }

        public static PasswordHash of(String value){
            return new PasswordHash(value);
        }


        @Override
        public String toString() {
            return value;
        }
    }
