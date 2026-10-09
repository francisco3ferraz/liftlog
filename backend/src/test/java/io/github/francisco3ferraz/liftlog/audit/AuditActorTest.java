package io.github.francisco3ferraz.liftlog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuditActorTest {

    @Test
    void storedFormNamesTheKindOfActor() {
        var id = UUID.fromString("0199c6a0-0000-7000-8000-000000000001");

        assertThat(AuditActor.user(id).value()).isEqualTo("user:0199c6a0-0000-7000-8000-000000000001");
        assertThat(AuditActor.system("auto-finish").value()).isEqualTo("system:auto-finish");
        assertThat(AuditActor.anonymous().value()).isEqualTo("anonymous");
    }

    @Test
    void onlyAUserActorHasAUserId() {
        var id = UUID.fromString("0199c6a0-0000-7000-8000-000000000001");

        assertThat(AuditActor.user(id).userId()).contains(id);
        assertThat(AuditActor.system("bootstrap").userId()).isEmpty();
        assertThat(AuditActor.anonymous().userId()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Auto-Finish", "auto finish", "auto--finish", "-auto", "auto:finish"})
    void systemActorNamesMustBeKebabCase(String name) {
        assertThatIllegalArgumentException().isThrownBy(() -> AuditActor.system(name));
    }

    @Test
    void aBlankRequestIdIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AuditContext.runAs(AuditActor.anonymous(), " ", () -> {}));
    }
}
