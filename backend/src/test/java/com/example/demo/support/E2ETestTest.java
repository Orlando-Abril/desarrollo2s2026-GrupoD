package com.example.demo.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class E2ETestTest {

    @Test
    void composesTheCompleteEndToEndTestContract() {
        Target target = E2ETest.class.getAnnotation(Target.class);
        Retention retention = E2ETest.class.getAnnotation(Retention.class);
        SpringBootTest springBootTest = E2ETest.class.getAnnotation(SpringBootTest.class);
        ActiveProfiles activeProfiles = E2ETest.class.getAnnotation(ActiveProfiles.class);
        Tag tag = E2ETest.class.getAnnotation(Tag.class);
        ExtendWith extendWith = E2ETest.class.getAnnotation(ExtendWith.class);

        assertThat(target.value()).containsExactly(ElementType.TYPE);
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(springBootTest.webEnvironment()).isEqualTo(SpringBootTest.WebEnvironment.RANDOM_PORT);
        assertThat(activeProfiles.value()).containsExactly("e2e");
        assertThat(tag.value()).isEqualTo("e2e");
        assertThat(Arrays.asList(extendWith.value()))
                .containsExactly(ExternalServicesAvailableCondition.class, E2EDatabaseCleaner.class);
        assertThat(AfterEachCallback.class).isAssignableFrom(E2EDatabaseCleaner.class);
    }
}
