package in.ggv.campora.web.form;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Long enough, short enough to hash (BCrypt reads 72 bytes), and not one of the first guesses. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GoodPassword.Check.class)
public @interface GoodPassword {

    String message() default "Choose a stronger password";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Check implements ConstraintValidator<GoodPassword, String> {

        private static final Set<String> COMMON = Set.of("password", "passw0rd", "password1", "password12",
                "password123", "password@123", "pass1234", "pass@123", "12345678", "123456789", "1234567890",
                "87654321", "0123456789", "11223344", "12341234", "qwertyui", "qwertyuiop", "qwerty123", "qwerty@123",
                "asdfghjkl", "1q2w3e4r", "1qaz2wsx", "abcd1234", "abcd@1234", "abc12345", "abcdefgh", "admin123",
                "admin@123", "admin1234", "welcome1", "welcome123", "welcome@123", "iloveyou", "sunshine",
                "football", "computer", "india123", "india@123", "student123", "student@123", "driver123", "driver@123",
                "campora123", "campora@123", "bilaspur", "bilaspur123", "bilaspur@123");

        @Override
        public boolean isValid(String password, ConstraintValidatorContext context) {
            if (password == null || password.isBlank()) {
                return true; // @NotBlank speaks for this
            }
            String problem = null;
            if (password.length() < 8 || password.length() > 64) {
                problem = "Use 8 to 64 characters";
            } else if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
                problem = "That password is too long; use fewer characters";
            } else if (COMMON.contains(password.toLowerCase(Locale.ROOT)) || password.chars().distinct().count() < 4) {
                problem = "That password is too easy to guess";
            }
            if (problem == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(problem).addConstraintViolation();
            return false;
        }
    }
}
