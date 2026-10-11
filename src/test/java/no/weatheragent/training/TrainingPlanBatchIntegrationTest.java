package no.weatheragent.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.web.dto.SavePlanRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Run against an isolated PostgreSQL database using PLAN_QA_JDBC_URL. Never production. */
@EnabledIfEnvironmentVariable(named = "PLAN_QA_JDBC_URL", matches = ".+")
class TrainingPlanBatchIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("PLAN_QA_JDBC_URL"), "postgres", "");
        }
        @Bean DataSourceTransactionManager transactionManager(DriverManagerDataSource ds) {
            return new DataSourceTransactionManager(ds);
        }
        @Bean JdbcTemplate jdbc(DriverManagerDataSource ds) { return new JdbcTemplate(ds); }
        @Bean TrainingPlanService plans() { return mock(TrainingPlanService.class); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean TrainingPlanBatchService batches(JdbcTemplate jdbc, TrainingPlanService plans, ObjectMapper mapper) {
            return new TrainingPlanBatchService(jdbc, plans, mapper);
        }
    }
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TrainingPlanService plans;
    private TrainingPlanBatchService batches;
    private final UUID owner = UUID.randomUUID();
    private final UUID request = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger writes = new AtomicInteger();

    @BeforeEach
    void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        plans = context.getBean(TrainingPlanService.class);
        batches = context.getBean(TrainingPlanBatchService.class);
        jdbc.execute("drop table if exists training_plan_batches");
        jdbc.execute("drop table if exists qa_saved_plans");
        jdbc.execute("create table qa_saved_plans(user_id uuid, title text)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V19__atomic_plan_acceptance.sql")));
        when(plans.save(any(), any(), any(), any(), any())).thenAnswer(call -> {
            writes.incrementAndGet();
            UUID user = call.getArgument(0);
            String title = call.getArgument(1);
            jdbc.update("insert into qa_saved_plans values (?, ?)", user, title);
            return new TrainingPlan(user, title, call.getArgument(2), call.getArgument(3), call.getArgument(4));
        });
    }
    @AfterEach void cleanup() { if (context != null) context.close(); }
    private List<SavePlanRequest> input() {
        return List.of(new SavePlanRequest("A", "STYRKE", mapper.createObjectNode(), null),
                new SavePlanRequest("B", "STYRKE", mapper.createObjectNode(), null));
    }

    @Test
    void lostResponseRetryReturnsReceiptWithoutWritingAgain() {
        var original = batches.save(owner, request, input());
        assertEquals(original, batches.save(owner, request, input()));
        assertEquals(2, writes.get());
        assertEquals(2, jdbc.queryForObject("select count(*) from qa_saved_plans", Integer.class));
    }
    @Test
    void failingSecondSaveRollsBackFirstSaveAndReceipt() {
        doThrow(new IllegalArgumentException("limit")).when(plans).save(any(), eq("B"), any(), any(), any());
        assertThrows(IllegalArgumentException.class, () -> batches.save(owner, request, input()));
        assertEquals(0, jdbc.queryForObject("select count(*) from qa_saved_plans", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from training_plan_batches", Integer.class));
    }
    @Test
    void simultaneousRetriesCreateOnlyOneBatch() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var gate = new CountDownLatch(1);
            var calls = java.util.stream.IntStream.range(0, 4).mapToObj(i -> executor.submit(() -> {
                gate.await();
                return batches.save(owner, request, input());
            })).toList();
            gate.countDown();
            var first = calls.getFirst().get(10, TimeUnit.SECONDS);
            for (var call : calls) assertEquals(first, call.get(10, TimeUnit.SECONDS));
            assertEquals(2, writes.get());
        }
    }
    @Test
    void requestIdIsScopedToOwnerAndCannotBeReusedForDifferentContent() {
        batches.save(owner, request, input());
        assertThrows(IllegalArgumentException.class, () -> batches.save(owner, request, input().subList(0, 1)));
        batches.save(UUID.randomUUID(), request, input());
        assertEquals(4, writes.get());
        assertEquals(2, jdbc.queryForObject("select count(*) from training_plan_batches", Integer.class));
    }
    @Test
    void receiptTableIsLockedDown() {
        assertEquals(true, jdbc.queryForObject(
                "select relrowsecurity from pg_class where relname = 'training_plan_batches'", Boolean.class));
    }
}
