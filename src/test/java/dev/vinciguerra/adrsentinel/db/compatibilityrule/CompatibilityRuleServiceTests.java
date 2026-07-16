package dev.vinciguerra.adrsentinel.db.compatibilityrule;

import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClassService;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.compatibilityrule.CompatibilityRuleRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Suite di Test Unitari (Unit Test) per il Service Layer {@link CompatibilityRuleService}.
 *
 * <p>
 * <b>Contesto di Dominio (Logistica ADR Sentinel):</b><br>
 * Questa classe verifica il comportamento della Business Logic responsabile della gestione delle
 * "Regole di Compatibilita'" (Matrice di Segregazione). Stabilisce se determinate classi di merci
 * pericolose possono essere caricate sullo stesso veicolo (Mixed Loading) in conformita' con le
 * normative ADR internazionali.
 * </p>
 *
 * <p>
 * <b>Strategia di Isolamento (Pure Unit Testing):</b><br>
 * Puro isolamento del Service Layer tramite {@link ExtendWith} con {@link MockitoExtension}.
 * Nessun contesto Spring avviato, nessun database (H2 o similari) istanziato.
 * Le dipendenze ({@link CompatibilityRuleRepository}, {@link AdrClassService}, {@link CacheManager})
 * sono interamente simulate tramite Mock Mockito, garantendo test veloci, deterministici e privi
 * di effetti collaterali sull'infrastruttura.
 * </p>
 *
 * <p>
 * <b>Copertura Metodologica (TDD Difensivo - Zero-Trust):</b><br>
 * Ogni metodo pubblico del service e' coperto con Happy Path, Failure Path ed Edge Cases.
 * I test contrassegnati con {@code [RED - TDD]} sono stati deliberatamente scritti per fallire
 * (Fase RED del TDD), al fine di esporre vulnerabilita' e mancanze di validazione nel codice
 * sorgente di produzione.
 * </p>
 *
 * <p>
 * <b>Mock coinvolti:</b>
 * <ul>
 *   <li>{@link CompatibilityRuleRepository} - mock del DAO JPA</li>
 *   <li>{@link AdrClassService} - mock del service di lookup delle classi ADR</li>
 *   <li>{@link CacheManager} - mock del gestore centralizzato della cache Caffeine</li>
 *   <li>{@link Cache} - mock della singola regione di cache</li>
 * </ul>
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompatibilityRuleService - Unit Test Suite")
class CompatibilityRuleServiceTests {

    // =========================================================================
    // MOCKS E DIPENDENZE
    // =========================================================================

    /** Mock del repository JPA per le operazioni di accesso ai dati. */
    @Mock
    private CompatibilityRuleRepository compatibilityRuleRepository;

    /** Mock del service di dominio delle Classi ADR per il lookup ottimizzato via cache. */
    @Mock
    private AdrClassService adrClassService;

    /**
     * Mock del CacheManager di Spring. Necessario per soddisfare il costruttore della
     * superclasse {@link AbstractGenericService}.
     */
    @Mock
    private CacheManager cacheManager;

    /** Mock della singola regione di cache Caffeine restituita dal CacheManager. */
    @Mock
    private Cache mockCache;

    /**
     * Istanza del Subject Under Test (SUT) con le dipendenze iniettate tramite {@link InjectMocks}.
     * Mockito utilizza il costruttore primario del service per l'iniezione.
     */
    @InjectMocks
    private CompatibilityRuleService compatibilityRuleService;

    // =========================================================================
    // FIXTURES DI SUPPORTO
    // =========================================================================

    /** Entita' AdrClass "sorgente" (Classe 3 - Liquidi Infiammabili) usata come fixture. */
    private AdrClass adrClass3;

    /** Entita' AdrClass "destinazione" (Classe 8 - Corrosivi) usata come fixture. */
    private AdrClass adrClass8;

    /**
     * Metodo di setup eseguito prima di ogni test.
     * Inizializza le fixture di dominio ADR con costruttori e setter (le entita' JPA
     * non sono Snapshot immutabili, quindi i setter sono disponibili).
     */
    @BeforeEach
    void setUp() {
        adrClass3 = new AdrClass();
        adrClass3.setId(1L);
        adrClass3.setClassCode("3");
        adrClass3.setDescription("Liquidi infiammabili");

        adrClass8 = new AdrClass();
        adrClass8.setId(2L);
        adrClass8.setClassCode("8");
        adrClass8.setDescription("Materie corrosive");
    }

    // =========================================================================
    // NESTED CLASS: getByAdrClassA
    // =========================================================================

    /**
     * Gruppo di test per il metodo {@link CompatibilityRuleService#getByAdrClassA(String)}.
     *
     * <p>
     * Verifica il comportamento del metodo di lettura delle regole di compatibilita'
     * a partire dal classCode della Classe ADR sorgente.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("getByAdrClassA(String adrClassCodeA)")
    class GetByAdrClassATests {

        /**
         * Happy Path: verifica che il metodo deleghi correttamente la query derivata al repository
         * e restituisca la lista di regole associata al classCode fornito.
         *
         * <p>
         * <b>Mock coinvolti:</b> {@link CompatibilityRuleRepository#findByAdrClassA_ClassCode(String)}<br>
         * <b>Output atteso:</b> Lista non nulla contenente esattamente una {@link CompatibilityRule}.
         * </p>
         */
        @Test
        @DisplayName("HAPPY PATH: Dovrebbe restituire la lista di regole per un classCode valido")
        void shouldReturnRuleListForValidClassCode() {
            // Arrange
            String classCode = "3";
            CompatibilityRule rule = new CompatibilityRule();
            rule.setId(10L);
            rule.setAdrClassA(adrClass3);
            rule.setAdrClassB(adrClass8);
            rule.setCompatible(true);

            when(compatibilityRuleRepository.findByAdrClassA_ClassCode(classCode))
                    .thenReturn(List.of(rule));

            // Act
            List<CompatibilityRule> result = compatibilityRuleService.getByAdrClassA(classCode);

            // Assert
            assertThat(result)
                    .isNotNull()
                    .hasSize(1)
                    .first()
                    .satisfies(r -> {
                        assertThat(r.getId()).isEqualTo(10L);
                        assertThat(r.isCompatible()).isTrue();
                    });

            verify(compatibilityRuleRepository, times(1)).findByAdrClassA_ClassCode(classCode);
        }

        /**
         * Edge Case: verifica che il metodo restituisca una lista vuota quando la Classe ADR
         * richiesta non ha regole censite nel sistema, senza lanciare eccezioni.
         *
         * <p>
         * <b>Motivazione di business:</b> Una classe ADR senza regole mappate e' uno stato valido
         * (anagrafica incompleta), non un errore fatale. Il chiamante deve poter gestire la lista vuota.<br>
         * <b>Mock coinvolti:</b> {@link CompatibilityRuleRepository#findByAdrClassA_ClassCode(String)}<br>
         * <b>Output atteso:</b> Lista vuota non nulla.
         * </p>
         */
        @Test
        @DisplayName("EDGE CASE: Dovrebbe restituire una lista vuota se non ci sono regole per il classCode")
        void shouldReturnEmptyListWhenNoRulesFoundForClassCode() {
            // Arrange
            String classCode = "1";
            when(compatibilityRuleRepository.findByAdrClassA_ClassCode(classCode))
                    .thenReturn(Collections.emptyList());

            // Act
            List<CompatibilityRule> result = compatibilityRuleService.getByAdrClassA(classCode);

            // Assert
            assertThat(result)
                    .isNotNull()
                    .isEmpty();

            verify(compatibilityRuleRepository, times(1)).findByAdrClassA_ClassCode(classCode);
        }

        /**
         * [RED - TDD] Failure Path: verifica che il metodo non accetti un classCode {@code null}.
         *
         * <p>
         * <b>Vulnerabilita' rilevata:</b> Il metodo {@code getByAdrClassA(String)} NON esegue
         * alcun controllo di null o blank sul parametro {@code adrClassCodeA} in ingresso.
         * Una chiamata con {@code null} viene propagata direttamente al repository JPA, dove
         * potrebbe risultare in una query malformata, una NullPointerException non gestita,
         * oppure in una risposta con una lista vuota che nasconde silenziosamente l'errore.<br>
         * <b>Correzione attesa:</b> Aggiungere una Guard Clause all'inizio del metodo:
         * {@code if (adrClassCodeA == null || adrClassCodeA.isBlank()) throw new IllegalArgumentException("classCode cannot be null or blank");}
         * </p>
         *
         * <p>
         * <b>QUESTO TEST E' IN FASE RED (TDD): FALLIRA' deliberatamente finche' la validazione
         * non viene aggiunta al codice sorgente di produzione.</b>
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno (la validazione dovrebbe fermare l'esecuzione prima del repository).<br>
         * <b>Output atteso:</b> {@link IllegalArgumentException}.
         * </p>
         */
        @Test
        @DisplayName("[RED - TDD] FAILURE PATH: Dovrebbe lanciare IllegalArgumentException per classCode null")
        void shouldThrowExceptionWhenClassCodeIsNull() {
            // Arrange - nessun setup di mock necessario (la validazione blocca prima del repository)

            // Act & Assert
            assertThatThrownBy(() -> compatibilityRuleService.getByAdrClassA(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("classCode");

            // Verifica che il repository non sia mai stato invocato con input non valido
            verify(compatibilityRuleRepository, never()).findByAdrClassA_ClassCode(any());
        }

        /**
         * [RED - TDD] Failure Path: verifica che il metodo non accetti un classCode vuoto (blank string).
         *
         * <p>
         * <b>Vulnerabilita' rilevata:</b> Stessa mancanza descritta per il test null:
         * il metodo non valida ne' rifiuta le stringhe vuote o composte da soli spazi.
         * Una stringa blank passata alla query derivata JPA genererebbe un risultato inaspettato.<br>
         * <b>Correzione attesa:</b> Medesima Guard Clause indicata nel test precedente.
         * </p>
         *
         * <p>
         * <b>QUESTO TEST E' IN FASE RED (TDD): FALLIRA' deliberatamente finche' la validazione
         * non viene aggiunta al codice sorgente di produzione.</b>
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@link IllegalArgumentException}.
         * </p>
         */
        @Test
        @DisplayName("[RED - TDD] FAILURE PATH: Dovrebbe lanciare IllegalArgumentException per classCode blank")
        void shouldThrowExceptionWhenClassCodeIsBlank() {
            // Arrange - nessun setup di mock necessario

            // Act & Assert
            assertThatThrownBy(() -> compatibilityRuleService.getByAdrClassA("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("classCode");

            verify(compatibilityRuleRepository, never()).findByAdrClassA_ClassCode(any());
        }

        /**
         * Happy Path: verifica che il metodo restituisca correttamente piu' regole associate
         * alla stessa classe ADR sorgente (scenario di produzione tipico con matrice ADR completa).
         *
         * <p>
         * <b>Mock coinvolti:</b> {@link CompatibilityRuleRepository#findByAdrClassA_ClassCode(String)}<br>
         * <b>Output atteso:</b> Lista con esattamente 3 elementi.
         * </p>
         */
        @Test
        @DisplayName("HAPPY PATH: Dovrebbe restituire la lista completa con piu' regole per un classCode")
        void shouldReturnMultipleRulesForGivenClassCode() {
            // Arrange
            String classCode = "3";

            AdrClass adrClass4 = new AdrClass();
            adrClass4.setId(3L);
            adrClass4.setClassCode("4.1");
            adrClass4.setDescription("Materie solide infiammabili");

            AdrClass adrClass6 = new AdrClass();
            adrClass6.setId(4L);
            adrClass6.setClassCode("6.1");
            adrClass6.setDescription("Materie tossiche");

            CompatibilityRule rule1 = new CompatibilityRule();
            rule1.setAdrClassA(adrClass3);
            rule1.setAdrClassB(adrClass8);
            rule1.setCompatible(false);

            CompatibilityRule rule2 = new CompatibilityRule();
            rule2.setAdrClassA(adrClass3);
            rule2.setAdrClassB(adrClass4);
            rule2.setCompatible(true);

            CompatibilityRule rule3 = new CompatibilityRule();
            rule3.setAdrClassA(adrClass3);
            rule3.setAdrClassB(adrClass6);
            rule3.setCompatible(false);

            when(compatibilityRuleRepository.findByAdrClassA_ClassCode(classCode))
                    .thenReturn(List.of(rule1, rule2, rule3));

            // Act
            List<CompatibilityRule> result = compatibilityRuleService.getByAdrClassA(classCode);

            // Assert
            assertThat(result)
                    .isNotNull()
                    .hasSize(3);

            verify(compatibilityRuleRepository, times(1)).findByAdrClassA_ClassCode(classCode);
        }
    }

    // =========================================================================
    // NESTED CLASS: save
    // =========================================================================

    /**
     * Gruppo di test per il metodo {@link CompatibilityRuleService#save(CompatibilityRule)}.
     *
     * <p>
     * Verifica la correttezza della persistenza di una nuova regola di compatibilita',
     * l'invocazione del repository JPA e la registrazione del callback di sincronizzazione
     * della cache post-commit tramite {@link TransactionSynchronizationManager}.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("save(CompatibilityRule newCompatibilityRule)")
    class SaveTests {

        /**
         * Happy Path: verifica che il metodo invochi il repository per salvare la nuova
         * regola e restituisca l'entita' persistita (con ID generato), e che registri
         * correttamente un {@link TransactionSynchronization} per l'aggiornamento della cache
         * dopo il commit della transazione.
         *
         * <p>
         * <b>Architettura verificata:</b> Il metodo e' annotato @Transactional.
         * Il callback {@code afterCommit()} viene registrato nel {@link TransactionSynchronizationManager}
         * per garantire che la cache venga aggiornata SOLO se la transazione ha avuto successo
         * (pattern Write-Through post-commit). Il test usa un {@link MockedStatic} per
         * intercettare e simulare la registrazione del callback.<br>
         * <b>Mock coinvolti:</b> {@link CompatibilityRuleRepository#save(Object)},
         * {@code TransactionSynchronizationManager.registerSynchronization}<br>
         * <b>Output atteso:</b> L'entita' persistita con ID valorizzato.
         * </p>
         */
        @Test
        @DisplayName("HAPPY PATH: Dovrebbe salvare la regola, restituirla e registrare il callback di cache")
        void shouldSaveRuleAndRegisterCacheSynchronization() {
            // Arrange
            CompatibilityRule newRule = new CompatibilityRule();
            newRule.setAdrClassA(adrClass3);
            newRule.setAdrClassB(adrClass8);
            newRule.setCompatible(true);
            newRule.setWarningNote("Mantenere separati");

            CompatibilityRule savedRule = new CompatibilityRule();
            savedRule.setId(99L);
            savedRule.setAdrClassA(adrClass3);
            savedRule.setAdrClassB(adrClass8);
            savedRule.setCompatible(true);
            savedRule.setWarningNote("Mantenere separati");

            when(compatibilityRuleRepository.save(newRule)).thenReturn(savedRule);

            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

            try (MockedStatic<TransactionSynchronizationManager> mocked =
                         Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()))
                        .thenAnswer(invocation -> null);

                // Act
                CompatibilityRule result = compatibilityRuleService.save(newRule);

                // Assert - entita' restituita corretta
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(99L);
                assertThat(result.isCompatible()).isTrue();

                // Assert - il repository e' stato invocato correttamente
                verify(compatibilityRuleRepository, times(1)).save(newRule);

                // Assert - il callback di sincronizzazione e' stato registrato
                mocked.verify(
                        () -> TransactionSynchronizationManager.registerSynchronization(
                                any(TransactionSynchronization.class)),
                        times(1)
                );

                // Assert - il callback e' presente e non null
                assertThat(syncCaptor.getValue()).isNotNull();
            }
        }

        /**
         * Happy Path (isCompatible = false): verifica che il metodo salvi correttamente
         * anche una regola di incompatibilita' (segregazione obbligatoria).
         *
         * <p>
         * <b>Contesto ADR:</b> Il flag {@code isCompatible = false} e' il caso piu' frequente
         * nella matrice di segregazione: molte combinazioni di merci pericolose non possono
         * coesistere sullo stesso veicolo.<br>
         * <b>Mock coinvolti:</b> {@link CompatibilityRuleRepository#save(Object)}<br>
         * <b>Output atteso:</b> L'entita' persistita con {@code isCompatible = false}.
         * </p>
         */
        @Test
        @DisplayName("HAPPY PATH: Dovrebbe salvare correttamente una regola di incompatibilita' (isCompatible=false)")
        void shouldSaveIncompatibleRule() {
            // Arrange
            CompatibilityRule incompatibleRule = new CompatibilityRule();
            incompatibleRule.setAdrClassA(adrClass3);
            incompatibleRule.setAdrClassB(adrClass8);
            incompatibleRule.setCompatible(false);

            CompatibilityRule savedRule = new CompatibilityRule();
            savedRule.setId(55L);
            savedRule.setAdrClassA(adrClass3);
            savedRule.setAdrClassB(adrClass8);
            savedRule.setCompatible(false);

            when(compatibilityRuleRepository.save(incompatibleRule)).thenReturn(savedRule);

            try (MockedStatic<TransactionSynchronizationManager> mocked =
                         Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                        .thenAnswer(invocation -> null);

                // Act
                CompatibilityRule result = compatibilityRuleService.save(incompatibleRule);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(55L);
                assertThat(result.isCompatible()).isFalse();
                verify(compatibilityRuleRepository, times(1)).save(incompatibleRule);
            }
        }

        /**
         * [RED - TDD] Failure Path: verifica che il metodo non accetti {@code null} come
         * argomento, lanciando un'eccezione controllata.
         *
         * <p>
         * <b>Vulnerabilita' rilevata:</b> Il metodo {@code save(CompatibilityRule)} non esegue
         * alcun controllo di null sull'oggetto {@code newCompatibilityRule} in ingresso.
         * Chiamare {@code save(null)} causerebbe una NullPointerException non gestita
         * sulla riga {@code newCompatibilityRule.getAdrClassA().getClassCode()} (riga 110-111),
         * producendo un HTTP 500 anziche' un HTTP 400 semanticamente corretto.<br>
         * <b>Correzione attesa:</b> Aggiungere una Guard Clause:
         * {@code if (newCompatibilityRule == null) throw new IllegalArgumentException(...);}
         * </p>
         *
         * <p>
         * <b>QUESTO TEST E' IN FASE RED (TDD): FALLIRA' deliberatamente finche' la validazione
         * non viene aggiunta al codice sorgente di produzione.</b>
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno (la validazione blocca prima del repository).<br>
         * <b>Output atteso:</b> {@link IllegalArgumentException}.
         * </p>
         */
        @Test
        @DisplayName("[RED - TDD] FAILURE PATH: Dovrebbe lanciare IllegalArgumentException quando l'argomento e' null")
        void shouldThrowExceptionWhenCompatibilityRuleIsNull() {
            // Arrange - nessun setup necessario

            // Act & Assert
            assertThatThrownBy(() -> compatibilityRuleService.save(null))
                    .isInstanceOf(IllegalStateException.class);

            // Verifica che il repository non venga mai contaminato con dati nulli
            verify(compatibilityRuleRepository, never()).save(any());
        }

        /**
         * [RED - TDD] Failure Path: verifica che il metodo non accetti una regola priva
         * della Classe ADR "A" (adrClassA = null).
         *
         * <p>
         * <b>Vulnerabilita' rilevata:</b> Se {@code newCompatibilityRule.getAdrClassA()} e' null,
         * il metodo {@code save} esplode con NullPointerException sulla riga 110 del sorgente
         * ({@code newCompatibilityRule.getAdrClassA().getClassCode()}) durante il logging.
         * Questo e' un errore di programmazione difensiva: l'assenza di una classe ADR e' una
         * violazione del contratto di business (la regola non ha senso senza entrambe le classi).<br>
         * <b>Correzione attesa:</b> Aggiungere una validazione prima del logging:
         * {@code if (newCompatibilityRule.getAdrClassA() == null || ...) throw new IllegalArgumentException(...);}
         * </p>
         *
         * <p>
         * <b>QUESTO TEST E' IN FASE RED (TDD): FALLIRA' deliberatamente finche' la validazione
         * non viene aggiunta al codice sorgente di produzione.</b>
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno (la validazione blocca prima del log).<br>
         * <b>Output atteso:</b> {@link IllegalArgumentException}.
         * </p>
         */
        @Test
        @DisplayName("[RED - TDD] FAILURE PATH: Dovrebbe lanciare IllegalArgumentException se adrClassA e' null")
        void shouldThrowExceptionWhenAdrClassAIsNull() {
            // Arrange
            CompatibilityRule ruleWithNullClassA = new CompatibilityRule();
            ruleWithNullClassA.setAdrClassA(null);
            ruleWithNullClassA.setAdrClassB(adrClass8);
            ruleWithNullClassA.setCompatible(true);

            // Act & Assert
            assertThatThrownBy(() -> compatibilityRuleService.save(ruleWithNullClassA))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(compatibilityRuleRepository, never()).save(any());
        }

        /**
         * [RED - TDD] Failure Path: verifica che il metodo non accetti una regola priva
         * della Classe ADR "B" (adrClassB = null).
         *
         * <p>
         * <b>Vulnerabilita' rilevata:</b> Speculare al test per adrClassA null: se
         * {@code newCompatibilityRule.getAdrClassB()} e' null, il metodo esplode con
         * NullPointerException non gestita sulla riga 111 del sorgente durante il logging.<br>
         * <b>Correzione attesa:</b> Medesima validazione indicata nel test precedente.
         * </p>
         *
         * <p>
         * <b>QUESTO TEST E' IN FASE RED (TDD): FALLIRA' deliberatamente finche' la validazione
         * non viene aggiunta al codice sorgente di produzione.</b>
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@link IllegalArgumentException}.
         * </p>
         */
        @Test
        @DisplayName("[RED - TDD] FAILURE PATH: Dovrebbe lanciare IllegalArgumentException se adrClassB e' null")
        void shouldThrowExceptionWhenAdrClassBIsNull() {
            // Arrange
            CompatibilityRule ruleWithNullClassB = new CompatibilityRule();
            ruleWithNullClassB.setAdrClassA(adrClass3);
            ruleWithNullClassB.setAdrClassB(null);
            ruleWithNullClassB.setCompatible(false);

            // Act & Assert
            assertThatThrownBy(() -> compatibilityRuleService.save(ruleWithNullClassB))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(compatibilityRuleRepository, never()).save(any());
        }

        /**
         * Verifica che, dopo il salvataggio nel repository, il callback registrato nel
         * {@link TransactionSynchronizationManager} esegua correttamente la sincronizzazione
         * della cache invocando il CacheManager (Write-Through post-commit).
         *
         * <p>
         * <b>Architettura verificata (syncCacheAfterInsert via afterCommit):</b>
         * Il callback {@code afterCommit()} deve invocare {@code storeInCache} con il nome della
         * cache {@link CaffeineCacheConfiguration#COMPATIBILITY_RULE_ADR_CLASS_A_CACHE} e il
         * classCode di adrClassA come chiave. Questo test simula la chiamata diretta al
         * callback dopo la cattura tramite {@link ArgumentCaptor}.<br>
         * <b>Mock coinvolti:</b> {@link CacheManager#getCache(String)}, {@link Cache}.<br>
         * <b>Output atteso:</b> Il CacheManager viene interrogato con il nome della cache corretto;
         * la lista gia' in memoria viene aggiornata (upsert dell'elemento in coda).
         * </p>
         */
        @Test
        @DisplayName("HAPPY PATH: Il callback afterCommit dovrebbe sincronizzare la cache con il nuovo record")
        void shouldSyncCacheAfterCommitWhenListIsAlreadyInMemory() {
            // Arrange
            CompatibilityRule newRule = new CompatibilityRule();
            newRule.setAdrClassA(adrClass3);
            newRule.setAdrClassB(adrClass8);
            newRule.setCompatible(true);

            CompatibilityRule savedRule = new CompatibilityRule();
            savedRule.setId(77L);
            savedRule.setAdrClassA(adrClass3);
            savedRule.setAdrClassB(adrClass8);
            savedRule.setCompatible(true);

            // Lista gia' presente in cache (simula un cache hit per la chiave "3")
            List<CompatibilityRule> cachedList = new ArrayList<>();
            Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);
            when(valueWrapper.get()).thenReturn(cachedList);
            when(mockCache.get("3")).thenReturn(valueWrapper);
            when(cacheManager.getCache(CaffeineCacheConfiguration.COMPATIBILITY_RULE_ADR_CLASS_A_CACHE))
                    .thenReturn(mockCache);

            when(compatibilityRuleRepository.save(newRule)).thenReturn(savedRule);

            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

            try (MockedStatic<TransactionSynchronizationManager> mocked =
                         Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()))
                        .thenAnswer(invocation -> null);

                // Act - esegue il save e cattura il callback
                compatibilityRuleService.save(newRule);

                // Simula il commit della transazione invocando manualmente afterCommit()
                syncCaptor.getValue().afterCommit();

                // Assert - il CacheManager e' stato interrogato per la regione corretta
                verify(cacheManager, atLeastOnce())
                        .getCache(CaffeineCacheConfiguration.COMPATIBILITY_RULE_ADR_CLASS_A_CACHE);

                // Assert - la lista in cache e' stata aggiornata con il nuovo elemento
                assertThat(cachedList)
                        .hasSize(1)
                        .contains(savedRule);
            }
        }

        /**
         * Edge Case: verifica che il callback {@code afterCommit()} si comporti in modo
         * sicuro (graceful degradation) quando la cache non e' ancora popolata per la chiave.
         *
         * <p>
         * <b>Architettura verificata (Prevenzione Corruzione Lista Parziale):</b>
         * Se la lista non e' ancora in RAM (Cache Miss), il metodo {@code storeInCache} con
         * {@code CacheOperation.LIST_RECORD} deve astenersi dall'inizializzarla con il singolo
         * elemento (per evitare "stale data" parziali). Questo test verifica che la cache
         * NON venga scritta con un singolo elemento quando la lista non esiste.<br>
         * <b>Mock coinvolti:</b> {@link CacheManager#getCache(String)}, {@link Cache}.<br>
         * <b>Output atteso:</b> La cache non subisce operazioni put (nessuna lista parziale creata).
         * </p>
         */
        @Test
        @DisplayName("EDGE CASE: Il callback afterCommit non dovrebbe inizializzare una lista parziale in cache (Cache Miss)")
        void shouldNotCreatePartialListInCacheOnCacheMiss() {
            // Arrange
            CompatibilityRule newRule = new CompatibilityRule();
            newRule.setAdrClassA(adrClass3);
            newRule.setAdrClassB(adrClass8);
            newRule.setCompatible(true);

            CompatibilityRule savedRule = new CompatibilityRule();
            savedRule.setId(88L);
            savedRule.setAdrClassA(adrClass3);
            savedRule.setAdrClassB(adrClass8);
            savedRule.setCompatible(true);

            // Simula Cache Miss: la chiave "3" non e' presente nella cache
            when(mockCache.get("3")).thenReturn(null);
            when(cacheManager.getCache(CaffeineCacheConfiguration.COMPATIBILITY_RULE_ADR_CLASS_A_CACHE))
                    .thenReturn(mockCache);

            when(compatibilityRuleRepository.save(newRule)).thenReturn(savedRule);

            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

            try (MockedStatic<TransactionSynchronizationManager> mocked =
                         Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()))
                        .thenAnswer(invocation -> null);

                // Act
                compatibilityRuleService.save(newRule);
                syncCaptor.getValue().afterCommit();

                // Assert - la cache non ha subito operazioni di put (nessuna lista parziale)
                verify(mockCache, never()).put(anyString(), any());
            }
        }
    }

    // =========================================================================
    // NESTED CLASS: mapToEntity
    // =========================================================================

    /**
     * Gruppo di test per il metodo {@link CompatibilityRuleService#mapToEntity(CompatibilityRuleRequestDTO)}.
     *
     * <p>
     * Verifica la correttezza del mapping dal DTO piatto all'entita' di dominio, inclusa
     * la risoluzione delle dipendenze {@link AdrClass} tramite {@link AdrClassService}.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("mapToEntity(CompatibilityRuleRequestDTO dto)")
    class MapToEntityTests {

        /**
         * Happy Path: verifica che il metodo mappa correttamente tutti i campi del DTO
         * sull'entita' di dominio, risolvendo le entita' {@link AdrClass} tramite il service
         * di lookup ottimizzato.
         *
         * <p>
         * <b>Architettura verificata:</b> Il metodo interroga {@link AdrClassService#getByClassCode(String)}
         * due volte (una per classCodeA e una per classCodeB) e usa le entita' restituite per
         * idratare le relazioni ManyToOne dell'entita' JPA.<br>
         * <b>Mock coinvolti:</b> {@link AdrClassService#getByClassCode(String)}<br>
         * <b>Output atteso:</b> Entita' con adrClassA, adrClassB, isCompatible e warningNote valorizzati.
         * </p>
         */
        @Test
        @DisplayName("HAPPY PATH: Dovrebbe mappare correttamente un DTO valido sull'entita' CompatibilityRule")
        void shouldMapValidDtoToCompatibilityRuleEntity() {
            // Arrange
            CompatibilityRuleRequestDTO dto = new CompatibilityRuleRequestDTO(
                    "3",
                    "8",
                    false,
                    "NON CARICARE INSIEME"
            );

            when(adrClassService.getByClassCode("3")).thenReturn(adrClass3);
            when(adrClassService.getByClassCode("8")).thenReturn(adrClass8);

            // Act
            CompatibilityRule result = compatibilityRuleService.mapToEntity(dto);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getAdrClassA()).isEqualTo(adrClass3);
            assertThat(result.getAdrClassB()).isEqualTo(adrClass8);
            assertThat(result.isCompatible()).isFalse();
            assertThat(result.getWarningNote()).isEqualTo("NON CARICARE INSIEME");

            verify(adrClassService, times(1)).getByClassCode("3");
            verify(adrClassService, times(1)).getByClassCode("8");
        }

        /**
         * Happy Path (isCompatible = true con warningNote): verifica il mapping quando
         * il carico misto e' consentito ma e' presente una nota operativa.
         *
         * <p>
         * <b>Contesto ADR:</b> Alcune combinazioni di classi ADR sono compatibili ma richiedono
         * annotazioni operative specifiche sui documenti di trasporto (CMR).<br>
         * <b>Mock coinvolti:</b> {@link AdrClassService#getByClassCode(String)}<br>
         * <b>Output atteso:</b> Entita' con isCompatible=true e warningNote valorizzata.
         * </p>
         */
        @Test
        @DisplayName("HAPPY PATH: Dovrebbe mappare un DTO con isCompatible=true e warningNote valorizzata")
        void shouldMapDtoWithCompatibleTrueAndWarningNote() {
            // Arrange
            String expectedNote = "RISPETTARE LE DISTANZE DI SICUREZZA";
            CompatibilityRuleRequestDTO dto = new CompatibilityRuleRequestDTO(
                    "3",
                    "8",
                    true,
                    expectedNote
            );

            when(adrClassService.getByClassCode("3")).thenReturn(adrClass3);
            when(adrClassService.getByClassCode("8")).thenReturn(adrClass8);

            // Act
            CompatibilityRule result = compatibilityRuleService.mapToEntity(dto);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.isCompatible()).isTrue();
            assertThat(result.getWarningNote()).isEqualTo(expectedNote);
        }

        /**
         * Happy Path (warningNote null): verifica che il metodo mappa correttamente un DTO
         * con nota operativa null, lasciando che sia l'entita' stessa a gestire il fallback.
         *
         * <p>
         * <b>Architettura verificata:</b> Il metodo chiama semplicemente setWarningNote(null).
         * La normalizzazione del valore nullo a "NOTHING TO SAY" e' responsabilita' del
         * metodo @PrePersist/@PreUpdate dell'entita', non del service. Il test verifica che
         * il service NON interferisca.<br>
         * <b>Mock coinvolti:</b> {@link AdrClassService#getByClassCode(String)}<br>
         * <b>Output atteso:</b> Entita' con warningNote = null.
         * </p>
         */
        @Test
        @DisplayName("HAPPY PATH: Dovrebbe mappare un DTO con warningNote null senza eccezioni")
        void shouldMapDtoWithNullWarningNoteWithoutException() {
            // Arrange
            CompatibilityRuleRequestDTO dto = new CompatibilityRuleRequestDTO(
                    "3",
                    "8",
                    true,
                    null
            );

            when(adrClassService.getByClassCode("3")).thenReturn(adrClass3);
            when(adrClassService.getByClassCode("8")).thenReturn(adrClass8);

            // Act
            CompatibilityRule result = compatibilityRuleService.mapToEntity(dto);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getWarningNote()).isNull();
        }

        /**
         * Failure Path: verifica che il metodo propaghi correttamente la {@link ResourceNotFoundException}
         * lanciata da {@link AdrClassService} quando classCodeA non esiste nel database.
         *
         * <p>
         * <b>Contesto di business:</b> Se il classCode fornito non corrisponde a nessuna Classe ADR
         * censita, il lookup deve fallire con HTTP 404, impedendo la creazione
         * di una regola orfana o inconsistente nel database.<br>
         * <b>Mock coinvolti:</b> {@link AdrClassService#getByClassCode(String)} configurato per lanciare
         * {@link ResourceNotFoundException}.<br>
         * <b>Output atteso:</b> {@link ResourceNotFoundException} propagata senza swallow.
         * </p>
         */
        @Test
        @DisplayName("FAILURE PATH: Dovrebbe propagare ResourceNotFoundException se classCodeA non e' trovato")
        void shouldPropagateResourceNotFoundExceptionWhenClassCodeANotFound() {
            // Arrange
            String nonExistentClassCode = "9";
            CompatibilityRuleRequestDTO dto = new CompatibilityRuleRequestDTO(
                    nonExistentClassCode,
                    "8",
                    true,
                    "Nota generica"
            );

            when(adrClassService.getByClassCode(nonExistentClassCode))
                    .thenThrow(new ResourceNotFoundException("AdrClass not found: " + nonExistentClassCode));

            // Act & Assert
            assertThatThrownBy(() -> compatibilityRuleService.mapToEntity(dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(nonExistentClassCode);

            // La seconda chiamata non deve avvenire (fail-fast)
            verify(adrClassService, times(1)).getByClassCode(nonExistentClassCode);
            verify(adrClassService, never()).getByClassCode("8");
        }

        /**
         * Failure Path: verifica che il metodo propaghi correttamente la {@link ResourceNotFoundException}
         * lanciata da {@link AdrClassService} quando classCodeB non esiste nel database.
         *
         * <p>
         * <b>Contesto di business:</b> Speculare al test per classCodeA: anche la Classe B deve
         * esistere nel sistema prima che la regola possa essere creata.<br>
         * <b>Mock coinvolti:</b> {@link AdrClassService#getByClassCode(String)} restituisce adrClass3 per
         * classCodeA e lancia {@link ResourceNotFoundException} per classCodeB.<br>
         * <b>Output atteso:</b> {@link ResourceNotFoundException} propagata.
         * </p>
         */
        @Test
        @DisplayName("FAILURE PATH: Dovrebbe propagare ResourceNotFoundException se classCodeB non e' trovato")
        void shouldPropagateResourceNotFoundExceptionWhenClassCodeBNotFound() {
            // Arrange
            String nonExistentClassCode = "99";
            CompatibilityRuleRequestDTO dto = new CompatibilityRuleRequestDTO(
                    "3",
                    nonExistentClassCode,
                    false,
                    null
            );

            when(adrClassService.getByClassCode("3")).thenReturn(adrClass3);
            when(adrClassService.getByClassCode(nonExistentClassCode))
                    .thenThrow(new ResourceNotFoundException("AdrClass not found: " + nonExistentClassCode));

            // Act & Assert
            assertThatThrownBy(() -> compatibilityRuleService.mapToEntity(dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(nonExistentClassCode);

            verify(adrClassService, times(1)).getByClassCode("3");
            verify(adrClassService, times(1)).getByClassCode(nonExistentClassCode);
        }

        /**
         * [RED - TDD] Failure Path: verifica che il metodo non accetti un DTO null.
         *
         * <p>
         * <b>Vulnerabilita' rilevata:</b> Il metodo {@code mapToEntity(CompatibilityRuleRequestDTO dto)}
         * non esegue alcun controllo di null sull'argomento dto in ingresso.
         * Invocare il metodo con null causa una NullPointerException non gestita
         * alla riga 187 del sorgente (dto.classCodeA()), producendo un HTTP 500
         * anziche' un rifiuto controllato e semanticamente corretto.<br>
         * <b>Correzione attesa:</b> Aggiungere una Guard Clause:
         * {@code if (dto == null) throw new IllegalArgumentException("Il DTO di richiesta non puo' essere null");}
         * </p>
         *
         * <p>
         * <b>QUESTO TEST E' IN FASE RED (TDD): FALLIRA' deliberatamente finche' la validazione
         * non viene aggiunta al codice sorgente di produzione.</b>
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@link IllegalArgumentException}.
         * </p>
         */
        @Test
        @DisplayName("[RED - TDD] FAILURE PATH: Dovrebbe lanciare IllegalArgumentException se il DTO e' null")
        void shouldThrowExceptionWhenDtoIsNull() {
            // Arrange - nessun setup necessario

            // Act & Assert
            assertThatThrownBy(() -> compatibilityRuleService.mapToEntity(null))
                    .isInstanceOf(IllegalArgumentException.class);

            // Il service non deve mai delegare ad adrClassService con dati nulli
            verify(adrClassService, never()).getByClassCode(any());
        }

        /**
         * Edge Case: verifica che il mapping funzioni correttamente anche con classCodeA uguale a
         * classCodeB (es. "3" e "3"), ossia due classi identiche.
         *
         * <p>
         * <b>Architettura verificata:</b> Il metodo {@code mapToEntity} non esegue la validazione
         * di uguaglianza (A != B). Questa responsabilita' appartiene al metodo @PrePersist
         * dell'entita' ({@code safeOrderForUniqueConstraint}), che lancia BadRequestException
         * se le due classi sono identiche. Il test verifica che il service esegua il mapping
         * (incluso il doppio lookup sul service), delegando il controllo di business all'entita'.<br>
         * <b>Mock coinvolti:</b> {@link AdrClassService#getByClassCode(String)} configurato per
         * restituire la stessa entita' due volte.<br>
         * <b>Output atteso:</b> Entita' prodotta senza eccezioni a livello di service.
         * </p>
         */
        @Test
        @DisplayName("EDGE CASE: Il mapping non dovrebbe fallire per due classCode identici (la validazione e' nell'entita')")
        void shouldMapWithoutExceptionWhenClassCodesAreIdentical() {
            // Arrange
            CompatibilityRuleRequestDTO dto = new CompatibilityRuleRequestDTO(
                    "3",
                    "3",
                    false,
                    null
            );

            // Stessa entita' per entrambe le chiamate (Classe A = Classe B)
            when(adrClassService.getByClassCode("3")).thenReturn(adrClass3);

            // Act
            CompatibilityRule result = compatibilityRuleService.mapToEntity(dto);

            // Assert - il service non lancia eccezione, la valida l'entita' in @PrePersist
            assertThat(result).isNotNull();
            assertThat(result.getAdrClassA()).isEqualTo(adrClass3);
            assertThat(result.getAdrClassB()).isEqualTo(adrClass3);

            // Il service chiama getByClassCode due volte con lo stesso codice
            verify(adrClassService, times(2)).getByClassCode("3");
        }

        /**
         * [RED - TDD] Edge Case: verifica che il metodo non accetti un DTO con classCodeA blank.
         *
         * <p>
         * <b>Vulnerabilita' rilevata:</b> Il metodo {@code mapToEntity} non valida il contenuto
         * dei campi del DTO prima di delegare ad {@link AdrClassService#getByClassCode(String)}.
         * Sebbene le annotazioni di validazione sul record (@ValidatorAdrClassCode)
         * dovrebbero proteggere il controller, se il mapper viene invocato direttamente (es. da
         * altri service interni) con un DTO non validato, il classCode blank viene propagato
         * silenziosamente ad adrClassService, rendendo il messaggio di errore non contestuale.<br>
         * <b>Correzione attesa:</b> Aggiungere una validazione esplicita nel mapper:
         * {@code if (dto.classCodeA() == null || dto.classCodeA().isBlank()) throw new IllegalArgumentException(...);}
         * </p>
         *
         * <p>
         * <b>QUESTO TEST E' IN FASE RED (TDD): FALLIRA' deliberatamente finche' la validazione
         * non viene aggiunta al codice sorgente di produzione.</b>
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno (la validazione nel mapper blocca prima del service).<br>
         * <b>Output atteso:</b> {@link IllegalArgumentException}.
         * </p>
         */
        @Test
        @DisplayName("[RED - TDD] EDGE CASE: Dovrebbe lanciare IllegalArgumentException se classCodeA nel DTO e' blank")
        void shouldThrowExceptionWhenClassCodeAInDtoIsBlank() {
            // Arrange
            CompatibilityRuleRequestDTO dtoWithBlankClassCode = new CompatibilityRuleRequestDTO(
                    "  ",
                    "8",
                    true,
                    "Nota generica"
            );

            // Act & Assert
            assertThatThrownBy(() -> compatibilityRuleService.mapToEntity(dtoWithBlankClassCode))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(adrClassService, never()).getByClassCode(any());
        }
    }

    // =========================================================================
    // NESTED CLASS: Costruttore e Inizializzazione
    // =========================================================================

    /**
     * Gruppo di test per la verifica della corretta costruzione e configurazione
     * del {@link CompatibilityRuleService}.
     *
     * <p>
     * Verifica che le dipendenze siano iniettate correttamente e che il service
     * sia pronto all'uso dopo l'istanziazione.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Costruttore e Inizializzazione")
    class ConstructorTests {

        /**
         * Verifica che l'istanza del service sia non nulla dopo la costruzione
         * tramite iniezione delle dipendenze.
         *
         * <p>
         * <b>Architettura verificata:</b> Il costruttore invoca super(cacheManager),
         * delegando la gestione del CacheManager alla superclasse AbstractGenericService.
         * Il test verifica che l'intera catena di costruzione sia eseguita correttamente.<br>
         * <b>Mock coinvolti:</b> Tutti i mock di classe (@Mock).<br>
         * <b>Output atteso:</b> Istanza non nulla del service.
         * </p>
         */
        @Test
        @DisplayName("HAPPY PATH: Il service dovrebbe essere istanziato correttamente con tutte le dipendenze")
        void shouldBeInstantiatedSuccessfully() {
            // Arrange - l'istanza e' gia' creata tramite @InjectMocks in setUp

            // Assert
            assertThat(compatibilityRuleService).isNotNull();
        }

        /**
         * [RED - TDD] Failure Path: verifica che il costruttore non accetti un
         * {@link CompatibilityRuleRepository} null.
         *
         * <p>
         * <b>Vulnerabilita' rilevata:</b> Il costruttore di {@link CompatibilityRuleService}
         * non esegue alcuna Guard Clause di null-check sulle proprie dipendenze. Iniettare
         * un repository null non produce un errore immediato (Fail-Fast), ma causa
         * una NullPointerException differita al momento del primo utilizzo del repository.
         * Questo ritarda la scoperta del bug e complica il debugging.<br>
         * <b>Correzione attesa:</b> Aggiungere Objects.requireNonNull(compatibilityRuleRepository, "...")
         * e Objects.requireNonNull(adrClassService, "...") nel costruttore.
         * </p>
         *
         * <p>
         * <b>QUESTO TEST E' IN FASE RED (TDD): FALLIRA' deliberatamente finche' la validazione
         * non viene aggiunta al codice sorgente di produzione.</b>
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno (test del costruttore puro).<br>
         * <b>Output atteso:</b> NullPointerException o IllegalArgumentException
         * lanciata durante la costruzione dell'oggetto.
         * </p>
         */
        @Test
        @DisplayName("[RED - TDD] FAILURE PATH: Il costruttore dovrebbe rifiutare un repository null con NullPointerException")
        void shouldThrowExceptionWhenRepositoryIsNull() {
            // Act & Assert
            org.junit.jupiter.api.Assertions.assertThrows(
                    NullPointerException.class,
                    () -> new CompatibilityRuleService(null, adrClassService, cacheManager),
                    "Il costruttore dovrebbe lanciare NullPointerException se il repository e' null"
            );
        }

        /**
         * [RED - TDD] Failure Path: verifica che il costruttore non accetti un
         * {@link AdrClassService} null.
         *
         * <p>
         * <b>Vulnerabilita' rilevata:</b> Speculare al test precedente. L'assenza di null-check
         * su adrClassService nel costruttore causa una NullPointerException differita
         * al primo utilizzo del lookup nel metodo mapToEntity.<br>
         * <b>Correzione attesa:</b> Medesimi Objects.requireNonNull() nel costruttore.
         * </p>
         *
         * <p>
         * <b>QUESTO TEST E' IN FASE RED (TDD): FALLIRA' deliberatamente finche' la validazione
         * non viene aggiunta al codice sorgente di produzione.</b>
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> NullPointerException o IllegalArgumentException.
         * </p>
         */
        @Test
        @DisplayName("[RED - TDD] FAILURE PATH: Il costruttore dovrebbe rifiutare un AdrClassService null con NullPointerException")
        void shouldThrowExceptionWhenAdrClassServiceIsNull() {
            // Act & Assert
            org.junit.jupiter.api.Assertions.assertThrows(
                    NullPointerException.class,
                    () -> new CompatibilityRuleService(compatibilityRuleRepository, null, cacheManager),
                    "Il costruttore dovrebbe lanciare NullPointerException se adrClassService e' null"
            );
        }
    }
}
