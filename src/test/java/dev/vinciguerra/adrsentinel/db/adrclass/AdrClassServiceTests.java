package dev.vinciguerra.adrsentinel.db.adrclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.adrclass.AdrClassRequestDTO;

/**
 * Suite di test unitari per {@link AdrClassService}.
 *
 * <p>
 * Questa classe verifica in modo esaustivo e con isolamento puro il comportamento del Service
 * Layer dedicato alla gestione delle Classi ADR (macro-categorie di rischio normativa ADR).
 * Non viene avviato alcun contesto Spring, nessun database H2 e nessun broker di messaggi:
 * tutte le dipendenze esterne ({@link AdrClassRepository}, {@link CacheManager} e {@link Cache})
 * sono sostituite da mock Mockito, garantendo test veloci, deterministici e completamente isolati.
 * </p>
 *
 * <h3>Metodi sotto test:</h3>
 * <ul>
 *   <li>{@link AdrClassService#getByClassCode(String)} — lettura con logica cache @Cacheable</li>
 *   <li>{@link AdrClassService#getAllAdrClasses()} — lettura massiva con logica cache @Cacheable</li>
 *   <li>{@link AdrClassService#save(AdrClass)} — persistenza con sincronizzazione cache Write-Through</li>
 *   <li>{@link AdrClassService#mapToEntity(AdrClassRequestDTO)} — mapping DTO a Entity</li>
 *   <li>{@code syncCacheAfterInsert(AdrClass)} — metodo privato coperto via Reflection</li>
 * </ul>
 *
 * <h3>Strategia TDD applicata:</h3>
 * I test marcati con {@code [TDD-RED]} sono scritti intenzionalmente per FALLIRE con il codice
 * sorgente attuale. Rappresentano la Fase RED del ciclo TDD e segnalano vulnerabilita'
 * architetturali che lo sviluppatore deve correggere per far diventare i test "verdi" (Fase GREEN).
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdrClassService - Suite di Test Unitari (Isolamento Puro)")
class AdrClassServiceTests {

    // =====================================================================
    // Mock delle dipendenze
    // =====================================================================

    /** Mock del repository JPA che simula l'interazione con il database MariaDB. */
    @Mock
    private AdrClassRepository adrClassRepository;

    /**
     * Mock del CacheManager di Spring. Viene iniettato nel costruttore di
     * {@link AdrClassService} tramite la superclasse {@link dev.vinciguerra.adrsentinel.db.AbstractGenericService}.
     */
    @Mock
    private CacheManager cacheManager;

    /**
     * Mock dell'istanza di cache specifica ({@code adr_class_by_class_code}).
     * Restituito da {@code cacheManager.getCache(...)} per simulare operazioni di put/evict/get.
     */
    @Mock
    private Cache mockSingleRecordCache;

    /**
     * Mock dell'istanza di cache globale ({@code all_adr_class}).
     * Restituito da {@code cacheManager.getCache(...)} per simulare operazioni sulla lista.
     */
    @Mock
    private Cache mockAllAdrClassCache;

    /**
     * Mock del wrapper ritornato da {@link Cache#get(Object)}.
     * Usato per simulare un Cache Hit nella lista globale.
     */
    @Mock
    private Cache.ValueWrapper mockValueWrapper;

    /** System Under Test (SUT): l'istanza reale di {@link AdrClassService}. */
    @InjectMocks
    private AdrClassService adrClassService;

    // =====================================================================
    // Helper / Factory Methods interni alla suite
    // =====================================================================

    /**
     * Factory method helper che costruisce un'istanza di {@link AdrClass}
     * con classCode e description gia' valorizzati, pronta per essere usata nei test.
     *
     * @param classCode il codice ADR (es. "3", "6.1").
     * @param description la descrizione del pericolo (es. "Liquidi infiammabili").
     * @return un'istanza di {@link AdrClass} nello stato Transient (senza ID).
     */
    private AdrClass buildAdrClass(String classCode, String description) {
        AdrClass adrClass = new AdrClass();
        adrClass.setClassCode(classCode);
        adrClass.setDescription(description);
        return adrClass;
    }

    /**
     * Factory method helper che costruisce un'istanza di {@link AdrClass}
     * con ID, classCode e description valorizzati (simula stato Persistent da DB).
     *
     * @param id l'ID surrogato assegnato dal database.
     * @param classCode il codice ADR.
     * @param description la descrizione del pericolo.
     * @return un'istanza di {@link AdrClass} nello stato Persistent (con ID).
     */
    private AdrClass buildPersistedAdrClass(Long id, String classCode, String description) {
        AdrClass adrClass = buildAdrClass(classCode, description);
        adrClass.setId(id);
        return adrClass;
    }

    // =====================================================================
    // Classe Innestata: getByClassCode(String)
    // =====================================================================

    /**
     * Classe innestata che raggruppa tutti i test unitari per il metodo
     * {@link AdrClassService#getByClassCode(String)}.
     *
     * <p>
     * Il metodo esegue una ricerca per Business Key ({@code classCode}) delegando al repository.
     * Quando la classe ADR e' presente nel DB, la restituisce. In assenza, lancia
     * {@link ResourceNotFoundException}. Il meccanismo {@code @Cacheable} e' a carico del
     * proxy Spring e non e' testabile in isolamento puro (verrebbe testato in un integration test);
     * qui verifichiamo che il metodo deleghi correttamente al repository quando la cache non intercetta.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("getByClassCode(String)")
    class GetByClassCodeTests {

        /**
         * [HAPPY PATH] Verifica che {@code getByClassCode} ritorni correttamente l'entita'
         * {@link AdrClass} quando il repository trova un record corrispondente al classCode fornito.
         *
         * <p>
         * <b>Mock coinvolti:</b> {@code adrClassRepository.findByClassCode("3")} restituisce
         * un {@link Optional} contenente un'entita' valida.
         * </p>
         * <b>Output atteso:</b> l'entita' non e' null, il classCode corrisponde a "3"
         * e l'ID e' quello assegnato dal mock (es. 1L).
         */
        @Test
        @DisplayName("[Happy Path] Trovata: restituisce l'entita' AdrClass per classCode valido")
        void shouldReturnAdrClassWhenClassCodeExists() {
            // Arrange
            AdrClass expectedAdrClass = buildPersistedAdrClass(1L, "3", "Liquidi infiammabili");
            when(adrClassRepository.findByClassCode("3")).thenReturn(Optional.of(expectedAdrClass));

            // Act
            AdrClass result = adrClassService.getByClassCode("3");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getClassCode()).isEqualTo("3");
            assertThat(result.getDescription()).isEqualTo("Liquidi infiammabili");
            assertThat(result.getId()).isEqualTo(1L);
            verify(adrClassRepository).findByClassCode("3");
        }

        /**
         * [FAILURE PATH] Verifica che {@code getByClassCode} lanci {@link ResourceNotFoundException}
         * quando il repository non trova nessuna classe ADR corrispondente al classCode fornito.
         *
         * <p>
         * Questo test verifica il comportamento Fail-Fast dichiarato nel Javadoc del Service:
         * l'assenza del record deve tradursi in un'eccezione semantica di dominio (404 a livello REST),
         * mai in un {@code null} restituito silenziosamente.
         * </p>
         * <p>
         * <b>Mock coinvolti:</b> {@code adrClassRepository.findByClassCode("99")} restituisce
         * {@code Optional.empty()}.
         * </p>
         * <b>Output atteso:</b> {@link ResourceNotFoundException} con messaggio contenente "99".
         */
        @Test
        @DisplayName("[Failure Path] Non trovata: lancia ResourceNotFoundException per classCode inesistente")
        void shouldThrowResourceNotFoundExceptionWhenClassCodeDoesNotExist() {
            // Arrange
            when(adrClassRepository.findByClassCode("99")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> adrClassService.getByClassCode("99"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
            verify(adrClassRepository).findByClassCode("99");
        }

        /**
         * [EDGE CASE] Verifica il comportamento del metodo per un classCode composto
         * (es. "6.1" - Materie tossiche, con punto decimale), che e' un formato valido
         * e frequente nella normativa ADR.
         *
         * <p>
         * <b>Mock coinvolti:</b> {@code adrClassRepository.findByClassCode("6.1")} restituisce
         * un {@link Optional} contenente un'entita' valida.
         * </p>
         * <b>Output atteso:</b> l'entita' e' restituita correttamente con classCode "6.1".
         */
        @Test
        @DisplayName("[Edge Case] classCode con punto (6.1): restituisce l'entita' ADR corretta")
        void shouldReturnAdrClassForDecimalClassCode() {
            // Arrange
            AdrClass expectedAdrClass = buildPersistedAdrClass(2L, "6.1", "Materie tossiche");
            when(adrClassRepository.findByClassCode("6.1")).thenReturn(Optional.of(expectedAdrClass));

            // Act
            AdrClass result = adrClassService.getByClassCode("6.1");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getClassCode()).isEqualTo("6.1");
        }

        /**
         * [EDGE CASE] Verifica il comportamento del metodo per un classCode con lettera finale
         * (es. "1.4S" - Esplosivi con pericolo minimo), che rappresenta il formato
         * massimamente complesso consentito dalla normativa ADR (4 caratteri).
         *
         * <p>
         * <b>Mock coinvolti:</b> {@code adrClassRepository.findByClassCode("1.4S")} restituisce
         * un {@link Optional} contenente un'entita' valida.
         * </p>
         * <b>Output atteso:</b> l'entita' e' restituita correttamente con classCode "1.4S".
         */
        @Test
        @DisplayName("[Edge Case] classCode alfanumerico (1.4S): restituisce l'entita' ADR corretta")
        void shouldReturnAdrClassForAlphanumericClassCode() {
            // Arrange
            AdrClass expectedAdrClass = buildPersistedAdrClass(3L, "1.4S", "Esplosivi");
            when(adrClassRepository.findByClassCode("1.4S")).thenReturn(Optional.of(expectedAdrClass));

            // Act
            AdrClass result = adrClassService.getByClassCode("1.4S");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getClassCode()).isEqualTo("1.4S");
        }

        /**
         * [TDD-RED] Verifica che {@code getByClassCode} lanci {@link IllegalArgumentException}
         * quando viene invocato con un classCode {@code null}.
         *
         * <p>
         * <b>QUESTO TEST E' PROGETTATO PER FALLIRE (Fase RED del TDD).</b><br>
         * Nel codice sorgente attuale ({@link AdrClassService#getByClassCode(String)}), non esiste
         * alcun controllo esplicito sul parametro {@code classCode} prima della delega al repository.
         * Se {@code classCode} e' {@code null}, la chiamata {@code adrClassRepository.findByClassCode(null)}
         * potrebbe: (a) lanciare {@code NullPointerException} non gestita (HTTP 500), oppure
         * (b) generare una query SQL malformata (eccezione JPA non semantica).
         * </p>
         * <p>
         * <b>Fix richiesto:</b> Aggiungere come prima istruzione di {@code getByClassCode}:
         * {@code if (classCode == null || classCode.isBlank()) throw new IllegalArgumentException("classCode cannot be null or blank");}
         * </p>
         * <b>Mock coinvolti:</b> nessuno (l'eccezione deve essere lanciata PRIMA della delega al repository).
         * <br>
         * <b>Output atteso:</b> {@link IllegalArgumentException} prima che il repository venga invocato.
         */
        @Test
        @DisplayName("[TDD-RED] Null Guard mancante: getByClassCode(null) dovrebbe lanciare IllegalArgumentException")
        void shouldThrowIllegalArgumentExceptionWhenClassCodeIsNull() {
            // Arrange - nessun mock necessario: l'eccezione deve scattare prima del repository

            // Act & Assert
            assertThatThrownBy(() -> adrClassService.getByClassCode(null))
                .isInstanceOf(IllegalArgumentException.class);

            // Il repository NON deve mai essere interrogato con un input null
            verify(adrClassRepository, never()).findByClassCode(any());
        }

        /**
         * [TDD-RED] Verifica che {@code getByClassCode} lanci {@link IllegalArgumentException}
         * quando viene invocato con una stringa blank (es. stringa di soli spazi).
         *
         * <p>
         * <b>QUESTO TEST E' PROGETTATO PER FALLIRE (Fase RED del TDD).</b><br>
         * Una stringa blank non e' un classCode ADR valido. Il metodo attuale non la valida
         * e inoltra silenziosamente la query al DB, generando un potenziale errore in produzione.
         * </p>
         * <p>
         * <b>Fix richiesto:</b> Medesima guard clause citata nel test per {@code null}.
         * </p>
         * <b>Output atteso:</b> {@link IllegalArgumentException} prima che il repository venga invocato.
         */
        @Test
        @DisplayName("[TDD-RED] Blank Guard mancante: getByClassCode(blank) dovrebbe lanciare IllegalArgumentException")
        void shouldThrowIllegalArgumentExceptionWhenClassCodeIsBlank() {
            // Arrange - nessun mock necessario

            // Act & Assert
            assertThatThrownBy(() -> adrClassService.getByClassCode("   "))
                .isInstanceOf(IllegalArgumentException.class);

            verify(adrClassRepository, never()).findByClassCode(any());
        }
    }

    // =====================================================================
    // Classe Innestata: getAllAdrClasses()
    // =====================================================================

    /**
     * Classe innestata che raggruppa tutti i test unitari per il metodo
     * {@link AdrClassService#getAllAdrClasses()}.
     *
     * <p>
     * Il metodo restituisce l'intera lista delle classi ADR presenti nel database.
     * Non lancia eccezioni: un repository vuoto restituisce una lista vuota (HTTP 200 OK).
     * La logica {@code @Cacheable} e' gestita dal proxy Spring e non e' direttamente
     * testabile in isolamento puro.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("getAllAdrClasses()")
    class GetAllAdrClassesTests {

        /**
         * [HAPPY PATH] Verifica che {@code getAllAdrClasses} ritorni correttamente la lista
         * completa delle entita' {@link AdrClass} quando il repository ne contiene.
         *
         * <p>
         * <b>Mock coinvolti:</b> {@code adrClassRepository.findAll()} restituisce una lista
         * con due entita' ADR rappresentative (Classe 3 e Classe 8).
         * </p>
         * <b>Output atteso:</b> lista non null, dimensione 2, contenente le entita' corrette.
         */
        @Test
        @DisplayName("[Happy Path] Repository popolato: restituisce la lista completa delle AdrClass")
        void shouldReturnAllAdrClassesWhenRepositoryIsNotEmpty() {
            // Arrange
            AdrClass class3 = buildPersistedAdrClass(1L, "3", "Liquidi infiammabili");
            AdrClass class8 = buildPersistedAdrClass(2L, "8", "Materie corrosive");
            List<AdrClass> expectedList = List.of(class3, class8);
            when(adrClassRepository.findAll()).thenReturn(expectedList);

            // Act
            List<AdrClass> result = adrClassService.getAllAdrClasses();

            // Assert
            assertThat(result).isNotNull().hasSize(2).containsExactlyInAnyOrder(class3, class8);
            verify(adrClassRepository).findAll();
        }

        /**
         * [EDGE CASE - Database Vuoto] Verifica che {@code getAllAdrClasses} ritorni una lista
         * vuota (e non lanci eccezioni) quando il repository non contiene alcun record.
         *
         * <p>
         * Questo e' il comportamento atteso e dichiarato nel Javadoc del Service: un database vuoto
         * deve produrre un HTTP 200 OK con body {@code []}, conforme agli standard REST.
         * </p>
         * <p>
         * <b>Mock coinvolti:</b> {@code adrClassRepository.findAll()} restituisce {@code Collections.emptyList()}.
         * </p>
         * <b>Output atteso:</b> lista non null e vuota, nessuna eccezione.
         */
        @Test
        @DisplayName("[Edge Case] Repository vuoto: restituisce lista vuota senza eccezioni (HTTP 200)")
        void shouldReturnEmptyListWhenRepositoryIsEmpty() {
            // Arrange
            when(adrClassRepository.findAll()).thenReturn(Collections.emptyList());

            // Act & Assert
            assertThatCode(() -> {
                List<AdrClass> result = adrClassService.getAllAdrClasses();
                assertThat(result).isNotNull().isEmpty();
            }).doesNotThrowAnyException();
            verify(adrClassRepository).findAll();
        }

        /**
         * [EDGE CASE - Singolo Record] Verifica che {@code getAllAdrClasses} gestisca
         * correttamente un catalogo contenente un'unica classe ADR (catalogo minimo ADR).
         *
         * <p>
         * <b>Mock coinvolti:</b> {@code adrClassRepository.findAll()} restituisce una lista con
         * un solo elemento.
         * </p>
         * <b>Output atteso:</b> lista con un solo elemento correttamente valorizzato.
         */
        @Test
        @DisplayName("[Edge Case] Singolo Record: restituisce lista con un solo elemento")
        void shouldReturnSingleElementListWhenRepositoryHasOneRecord() {
            // Arrange
            AdrClass singleClass = buildPersistedAdrClass(5L, "5.1", "Materie comburenti");
            when(adrClassRepository.findAll()).thenReturn(List.of(singleClass));

            // Act
            List<AdrClass> result = adrClassService.getAllAdrClasses();

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getClassCode()).isEqualTo("5.1");
        }
    }

    // =====================================================================
    // Classe Innestata: save(AdrClass)
    // =====================================================================

    /**
     * Classe innestata che raggruppa tutti i test unitari per il metodo
     * {@link AdrClassService#save(AdrClass)}.
     *
     * <p>
     * Il metodo salva una nuova entita' {@link AdrClass} nel database e, al termine della
     * transazione (hook {@code afterCommit}), sincronizza le due regioni di cache Caffeine
     * (record singolo e lista globale). I test su {@code syncCacheAfterInsert} vengono
     * coperti indirettamente tramite l'invocazione diretta (fuori contesto transazionale)
     * per garantire la massima copertura di branch.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("save(AdrClass)")
    class SaveTests {

        /**
         * Predispone il contesto transazionale simulato prima di ogni test della classe innestata.
         * Richiesto per evitare {@link java.lang.IllegalStateException} quando
         * {@code TransactionSynchronizationManager.registerSynchronization} viene invocato
         * dall'implementazione reale del metodo {@code save()}.
         */
        @BeforeEach
        void initAndClearTransactionContext() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
            TransactionSynchronizationManager.initSynchronization();
        }

        /**
         * [HAPPY PATH] Verifica che {@code save} persista correttamente l'entita' {@link AdrClass}
         * e ritorni l'istanza arricchita con l'ID autogenerato dal database.
         *
         * <p>
         * <b>Mock coinvolti:</b> {@code adrClassRepository.save(newAdrClass)} restituisce
         * l'entita' con ID valorizzato (simula il comportamento di Hibernate al commit).
         * </p>
         * <b>Output atteso:</b> l'entita' restituita non e' null e possiede l'ID assegnato dal DB mock.
         */
        @Test
        @DisplayName("[Happy Path] Entita' valida: persiste nel DB e restituisce l'entita' con ID generato")
        void shouldSaveAndReturnPersistedAdrClassWithId() {
            // Arrange
            AdrClass newAdrClass = buildAdrClass("3", "Liquidi infiammabili");
            AdrClass savedAdrClass = buildPersistedAdrClass(10L, "3", "Liquidi infiammabili");
            when(adrClassRepository.save(newAdrClass)).thenReturn(savedAdrClass);

            // Act
            AdrClass result = adrClassService.save(newAdrClass);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getClassCode()).isEqualTo("3");
            verify(adrClassRepository).save(newAdrClass);
        }

        /**
         * [FAILURE PATH] Verifica che {@code save} propaghi correttamente la
         * {@link org.springframework.dao.DataIntegrityViolationException} (o qualsiasi
         * {@link RuntimeException}) lanciata dal repository in caso di violazione
         * del vincolo di unicita' su {@code classCode} (UK constraint del DB).
         *
         * <p>
         * <b>Scenario:</b> Si tenta di inserire una Classe ADR "3" gia' presente nel database.
         * Il repository lancia un'eccezione di violazione di integrita' referenziale.
         * </p>
         * <p>
         * <b>Mock coinvolti:</b> {@code adrClassRepository.save(duplicateAdrClass)} lancia
         * {@link org.springframework.dao.DataIntegrityViolationException}.
         * </p>
         * <b>Output atteso:</b> la medesima eccezione viene propagata senza essere inghiottita.
         */
        @Test
        @DisplayName("[Failure Path] classCode duplicato: propaga DataIntegrityViolationException dal repository")
        void shouldPropagateDataIntegrityViolationExceptionWhenClassCodeIsDuplicate() {
            // Arrange
            AdrClass duplicateAdrClass = buildAdrClass("3", "Liquidi infiammabili (duplicato)");
            when(adrClassRepository.save(duplicateAdrClass))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                    "UK constraint violation: class_code '3' already exists"));

            // Act & Assert
            assertThatThrownBy(() -> adrClassService.save(duplicateAdrClass))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("class_code '3'");
        }

        /**
         * [TDD-RED] Verifica che {@code save} lanci {@link IllegalArgumentException}
         * quando viene invocato con un'entita' {@code null}.
         *
         * <p>
         * <b>QUESTO TEST E' PROGETTATO PER FALLIRE (Fase RED del TDD).</b><br>
         * Il metodo {@link AdrClassService#save(AdrClass)} accede immediatamente a
         * {@code newAdrClass.getClassCode()} per il logging (riga 105 del sorgente).
         * Se {@code newAdrClass} e' {@code null}, questa riga causera' una
         * {@link NullPointerException} non gestita (HTTP 500), anziche' un'eccezione
         * semantica con messaggio chiaro al chiamante.
         * </p>
         * <p>
         * <b>Fix richiesto:</b> Aggiungere come prima istruzione di {@code save}:
         * {@code if (newAdrClass == null) throw new IllegalArgumentException("AdrClass entity cannot be null");}
         * </p>
         * <b>Mock coinvolti:</b> nessuno (l'eccezione deve scattare PRIMA della delega al repository).
         * <br>
         * <b>Output atteso:</b> {@link IllegalArgumentException} (non {@code NullPointerException}).
         */
        @Test
        @DisplayName("[TDD-RED] Null Guard mancante: save(null) dovrebbe lanciare IllegalArgumentException (non NullPointerException)")
        void shouldThrowIllegalArgumentExceptionWhenSavingNullEntity() {
            // Act & Assert
            assertThatThrownBy(() -> adrClassService.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(NullPointerException.class);

            verify(adrClassRepository, never()).save(any());
        }

        /**
         * [TDD-RED] Verifica che {@code save} lanci {@link IllegalArgumentException}
         * quando viene invocato con un'entita' con {@code classCode} null.
         *
         * <p>
         * <b>QUESTO TEST E' PROGETTATO PER FALLIRE (Fase RED del TDD).</b><br>
         * Un'entita' con {@code classCode} null verrebbe passata al DB, violando il vincolo
         * {@code nullable = false} della colonna {@code class_code} e causando un'eccezione
         * JPA a livello di Hibernate non semantica e non controllata.
         * La validazione dei campi obbligatori dell'entita' dovrebbe avvenire nel Service,
         * non dipendere esclusivamente dal DB.
         * </p>
         * <p>
         * <b>Fix richiesto:</b> Aggiungere validazione:
         * {@code if (newAdrClass.getClassCode() == null || newAdrClass.getClassCode().isBlank()) throw new IllegalArgumentException(...);}
         * </p>
         * <b>Output atteso:</b> {@link IllegalArgumentException} prima della delega al repository.
         */
        @Test
        @DisplayName("[TDD-RED] classCode null nell'entity: save dovrebbe lanciare IllegalArgumentException prima del repository")
        void shouldThrowIllegalArgumentExceptionWhenClassCodeIsNullInEntity() {
            // Arrange
            AdrClass invalidEntity = new AdrClass();
            invalidEntity.setClassCode(null);
            invalidEntity.setDescription("Descrizione valida");

            // Act & Assert
            assertThatThrownBy(() -> adrClassService.save(invalidEntity))
                .isInstanceOf(IllegalArgumentException.class);

            verify(adrClassRepository, never()).save(any());
        }
    }

    // =====================================================================
    // Classe Innestata: syncCacheAfterInsert (copertura via Reflection)
    // =====================================================================

    /**
     * Classe innestata che testa il metodo privato
     * {@code AdrClassService#syncCacheAfterInsert(AdrClass)} tramite Java Reflection.
     *
     * <p>
     * Poiche' {@code syncCacheAfterInsert} e' un metodo privato invocato all'interno
     * del hook {@code afterCommit} del {@code TransactionSynchronizationManager},
     * la sua logica viene testata invocando direttamente il metodo tramite reflection
     * per bypassare l'incapsulamento e testarne il comportamento in isolamento totale,
     * senza la dipendenza dal ciclo transazionale reale di Spring.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("syncCacheAfterInsert - Copertura via Java Reflection")
    class SyncCacheAfterInsertTests {

        /**
         * [HAPPY PATH - Write-Through Sincrono] Verifica che {@code syncCacheAfterInsert}
         * invochi correttamente {@code cache.put(classCode, savedAdrClass)} per aggiornare
         * la cache del record singolo ({@code ADR_CLASS_BY_CLASS_CODE_CACHE}).
         *
         * <p>
         * <b>Mock coinvolti:</b>
         * <ul>
         *   <li>{@code cacheManager.getCache(ADR_CLASS_BY_CLASS_CODE_CACHE)} restituisce {@code mockSingleRecordCache}</li>
         *   <li>{@code cacheManager.getCache(ALL_ADR_CLASS_CACHE)} restituisce {@code null} (cache globale non disponibile)</li>
         *   <li>{@code mockSingleRecordCache.put(classCode, savedAdrClass)} viene verificato</li>
         * </ul>
         * </p>
         * <b>Output atteso:</b> {@code mockSingleRecordCache.put("3", savedAdrClass)} viene chiamato esattamente una volta.
         */
        @Test
        @DisplayName("[Happy Path] Cache record singolo: put() invocato correttamente su ADR_CLASS_BY_CLASS_CODE_CACHE")
        void shouldPutSingleRecordInCacheAfterInsert() throws Exception {
            // Arrange
            AdrClass savedAdrClass = buildPersistedAdrClass(1L, "3", "Liquidi infiammabili");
            when(cacheManager.getCache(CaffeineCacheConfiguration.ADR_CLASS_BY_CLASS_CODE_CACHE))
                .thenReturn(mockSingleRecordCache);
            when(cacheManager.getCache(CaffeineCacheConfiguration.ALL_ADR_CLASS_CACHE))
                .thenReturn(null);

            java.lang.reflect.Method syncMethod = AdrClassService.class
                .getDeclaredMethod("syncCacheAfterInsert", AdrClass.class);
            syncMethod.setAccessible(true);

            // Act
            syncMethod.invoke(adrClassService, savedAdrClass);

            // Assert
            verify(mockSingleRecordCache).put("3", savedAdrClass);
        }

        /**
         * [HAPPY PATH - Lista Globale Popolata] Verifica che {@code syncCacheAfterInsert}
         * aggiunga l'entita' alla lista globale in cache ({@code ALL_ADR_CLASS_CACHE})
         * quando la lista e' gia' caricata in memoria (Cache Hit sul value wrapper).
         *
         * <p>
         * Questa e' la logica di "Append to List" del pattern Write-Through ibrido:
         * evita un'invalidazione totale aggiungendo chirurgicamente il nuovo record.
         * </p>
         * <p>
         * <b>Mock coinvolti:</b>
         * <ul>
         *   <li>{@code cacheManager.getCache(ALL_ADR_CLASS_CACHE)} restituisce {@code mockAllAdrClassCache}</li>
         *   <li>{@code mockAllAdrClassCache.get(ALL_ADR_CLASS_KEY)} restituisce {@code mockValueWrapper} con lista preesistente</li>
         * </ul>
         * </p>
         * <b>Output atteso:</b> {@code mockAllAdrClassCache.put(...)} viene chiamato con la lista aggiornata contenente il nuovo elemento.
         */
        @Test
        @DisplayName("[Happy Path] Lista globale in cache: append dell'entita' nella ALL_ADR_CLASS_CACHE")
        void shouldAppendRecordToGlobalListCacheAfterInsert() throws Exception {
            // Arrange
            AdrClass existingClass = buildPersistedAdrClass(1L, "8", "Materie corrosive");
            AdrClass newClass = buildPersistedAdrClass(2L, "3", "Liquidi infiammabili");

            List<AdrClass> preExistingList = new ArrayList<>();
            preExistingList.add(existingClass);

            when(cacheManager.getCache(CaffeineCacheConfiguration.ADR_CLASS_BY_CLASS_CODE_CACHE))
                .thenReturn(mockSingleRecordCache);
            when(cacheManager.getCache(CaffeineCacheConfiguration.ALL_ADR_CLASS_CACHE))
                .thenReturn(mockAllAdrClassCache);
            when(mockAllAdrClassCache.get(CaffeineCacheConfiguration.ALL_ADR_CLASS_KEY))
                .thenReturn(mockValueWrapper);
            when(mockValueWrapper.get()).thenReturn(preExistingList);

            java.lang.reflect.Method syncMethod = AdrClassService.class
                .getDeclaredMethod("syncCacheAfterInsert", AdrClass.class);
            syncMethod.setAccessible(true);

            // Act
            syncMethod.invoke(adrClassService, newClass);

            // Assert - la lista aggiornata deve contenere anche il nuovo elemento
            ArgumentCaptor<Object> captorValue = ArgumentCaptor.forClass(Object.class);
            verify(mockAllAdrClassCache).put(
                org.mockito.ArgumentMatchers.eq(CaffeineCacheConfiguration.ALL_ADR_CLASS_KEY),
                captorValue.capture()
            );
            @SuppressWarnings("unchecked")
            List<AdrClass> updatedList = (List<AdrClass>) captorValue.getValue();
            assertThat(updatedList).contains(newClass).contains(existingClass);
        }

        /**
         * [EDGE CASE - Lista Non in Cache] Verifica che {@code syncCacheAfterInsert}
         * NON tenti di inizializzare la lista globale se non e' ancora presente in memoria
         * (Cache Miss), rispettando il pattern di Lazy Loading sicuro.
         *
         * <p>
         * <b>Contesto Architetturale:</b> Se il metodo inizializzasse la lista con un solo elemento,
         * la successiva richiesta di {@code findAll} troverebbe la chiave valorizzata e restituirebbe
         * solo quell'elemento, nascondendo tutti i restanti record dal DB (corruzione della cache).
         * </p>
         * <p>
         * <b>Mock coinvolti:</b> {@code mockAllAdrClassCache.get(ALL_ADR_CLASS_KEY)} restituisce {@code null} (Cache Miss).
         * </p>
         * <b>Output atteso:</b> {@code mockAllAdrClassCache.put(...)} NON viene chiamato per la lista globale.
         */
        @Test
        @DisplayName("[Edge Case] Lista globale non in cache (Cache Miss): nessun append, lista non inizializzata")
        void shouldNotInitializeGlobalListCacheWhenNotYetInMemory() throws Exception {
            // Arrange
            AdrClass newClass = buildPersistedAdrClass(2L, "3", "Liquidi infiammabili");

            when(cacheManager.getCache(CaffeineCacheConfiguration.ADR_CLASS_BY_CLASS_CODE_CACHE))
                .thenReturn(mockSingleRecordCache);
            when(cacheManager.getCache(CaffeineCacheConfiguration.ALL_ADR_CLASS_CACHE))
                .thenReturn(mockAllAdrClassCache);
            when(mockAllAdrClassCache.get(CaffeineCacheConfiguration.ALL_ADR_CLASS_KEY))
                .thenReturn(null);

            java.lang.reflect.Method syncMethod = AdrClassService.class
                .getDeclaredMethod("syncCacheAfterInsert", AdrClass.class);
            syncMethod.setAccessible(true);

            // Act
            syncMethod.invoke(adrClassService, newClass);

            // Assert - il put sulla cache globale NON deve avvenire
            verify(mockAllAdrClassCache, never()).put(
                org.mockito.ArgumentMatchers.eq(CaffeineCacheConfiguration.ALL_ADR_CLASS_KEY),
                any()
            );
        }

        /**
         * [EDGE CASE - Upsert in Lista Globale] Verifica che {@code syncCacheAfterInsert}
         * rimuova il vecchio record dalla lista globale prima di aggiungere quello aggiornato,
         * prevenendo la presenza di duplicati (logica Upsert del {@code AbstractGenericService}).
         *
         * <p>
         * Questo test verifica che se un'entita' con lo stesso {@code classCode} e' gia' presente
         * nella lista, venga sostituita dall'istanza aggiornata e non duplicata.
         * La logica si basa sull'implementazione di {@link AdrClass#equals(Object)}.
         * </p>
         * <p>
         * <b>Mock coinvolti:</b> lista preesistente contenente un'entita' con classCode "3";
         * nuova entita' con stesso classCode "3" ma description aggiornata.
         * </p>
         * <b>Output atteso:</b> la lista finale contiene UN SOLO elemento con classCode "3"
         * e la description aggiornata.
         */
        @Test
        @DisplayName("[Edge Case] Upsert in lista: entita' duplicata rimossa e sostituita (no ghost record)")
        void shouldReplaceExistingEntryInListCacheOnUpsert() throws Exception {
            // Arrange
            AdrClass oldVersion = buildPersistedAdrClass(1L, "3", "Descrizione vecchia");
            AdrClass updatedVersion = buildPersistedAdrClass(1L, "3", "Descrizione aggiornata");

            List<AdrClass> preExistingList = new ArrayList<>();
            preExistingList.add(oldVersion);

            when(cacheManager.getCache(CaffeineCacheConfiguration.ADR_CLASS_BY_CLASS_CODE_CACHE))
                .thenReturn(mockSingleRecordCache);
            when(cacheManager.getCache(CaffeineCacheConfiguration.ALL_ADR_CLASS_CACHE))
                .thenReturn(mockAllAdrClassCache);
            when(mockAllAdrClassCache.get(CaffeineCacheConfiguration.ALL_ADR_CLASS_KEY))
                .thenReturn(mockValueWrapper);
            when(mockValueWrapper.get()).thenReturn(preExistingList);

            java.lang.reflect.Method syncMethod = AdrClassService.class
                .getDeclaredMethod("syncCacheAfterInsert", AdrClass.class);
            syncMethod.setAccessible(true);

            // Act
            syncMethod.invoke(adrClassService, updatedVersion);

            // Assert - la lista deve contenere esattamente 1 elemento (no duplicati)
            ArgumentCaptor<Object> captorValue = ArgumentCaptor.forClass(Object.class);
            verify(mockAllAdrClassCache).put(
                org.mockito.ArgumentMatchers.eq(CaffeineCacheConfiguration.ALL_ADR_CLASS_KEY),
                captorValue.capture()
            );
            @SuppressWarnings("unchecked")
            List<AdrClass> updatedList = (List<AdrClass>) captorValue.getValue();
            assertThat(updatedList)
                .hasSize(1)
                .extracting(AdrClass::getDescription)
                .containsExactly("Descrizione aggiornata");
        }
    }

    // =====================================================================
    // Classe Innestata: mapToEntity(AdrClassRequestDTO)
    // =====================================================================

    /**
     * Classe innestata che raggruppa tutti i test unitari per il metodo
     * {@link AdrClassService#mapToEntity(AdrClassRequestDTO)}.
     *
     * <p>
     * Il metodo implementa il pattern Data Mapper / Assembler: converte un
     * {@link AdrClassRequestDTO} (oggetto di trasporto web) in un'entita' di dominio
     * {@link AdrClass} nello stato Transient (senza ID). E' il "filtro di confine"
     * tra il layer REST e il layer di Business Logic.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("mapToEntity(AdrClassRequestDTO)")
    class MapToEntityTests {

        /**
         * [HAPPY PATH] Verifica che {@code mapToEntity} converta correttamente i campi
         * del DTO nell'entita' {@link AdrClass} corrispondente, preservando i valori di
         * {@code classCode} e {@code description} senza alterazioni.
         *
         * <p>
         * <b>Nessun mock necessario:</b> il metodo non interagisce ne' con il repository
         * ne' con la cache. Testa esclusivamente la logica di mapping puro.
         * </p>
         * <b>Output atteso:</b> entita' non null, con classCode = "3" e description = "Liquidi infiammabili".
         * L'ID deve essere null (stato Transient, non ancora persistito).
         */
        @Test
        @DisplayName("[Happy Path] DTO valido: mappa correttamente classCode e description nell'entita' AdrClass")
        void shouldMapDtoToEntityWithCorrectFieldValues() {
            // Arrange
            AdrClassRequestDTO dto = new AdrClassRequestDTO("3", "Liquidi infiammabili");

            // Act
            AdrClass result = adrClassService.mapToEntity(dto);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getClassCode()).isEqualTo("3");
            assertThat(result.getDescription()).isEqualTo("Liquidi infiammabili");
            assertThat(result.getId()).isNull();
        }

        /**
         * [HAPPY PATH] Verifica che {@code mapToEntity} mappi correttamente un classCode
         * complesso con punto e lettera finale (es. "1.4S"), tipico della normativa ADR.
         *
         * <p>
         * Testa la corretta preservazione di tutti i caratteri del classCode senza troncamenti
         * o modifiche indesiderate durante il mapping.
         * </p>
         * <b>Output atteso:</b> classCode "1.4S" e description correttamente mappati.
         */
        @Test
        @DisplayName("[Happy Path] DTO con classCode composto (1.4S): mapping corretto senza alterazioni")
        void shouldMapDtoToEntityWithComplexClassCode() {
            // Arrange
            AdrClassRequestDTO dto = new AdrClassRequestDTO("1.4S", "Esplosivi con pericolo minimo");

            // Act
            AdrClass result = adrClassService.mapToEntity(dto);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getClassCode()).isEqualTo("1.4S");
            assertThat(result.getDescription()).isEqualTo("Esplosivi con pericolo minimo");
        }

        /**
         * [ISOLATION PATH] Verifica che le istanze prodotte da {@code mapToEntity} siano
         * indipendenti: due chiamate successive con DTO diversi devono produrre due oggetti
         * distinti senza condivisione di stato (no Singleton pattern implicito).
         *
         * <p>
         * Testa che il metodo utilizzi {@code new AdrClass()} ad ogni invocazione e non
         * ricicli o condivida istanze tra mapping successivi.
         * </p>
         * <b>Output atteso:</b> due entita' distinte con riferimenti diversi e classCode diversi.
         */
        @Test
        @DisplayName("[Isolation Path] Due mapping successivi: istanze separate e indipendenti")
        void shouldProduceSeparateInstancesOnConsecutiveMappings() {
            // Arrange
            AdrClassRequestDTO dto1 = new AdrClassRequestDTO("3", "Liquidi infiammabili");
            AdrClassRequestDTO dto2 = new AdrClassRequestDTO("8", "Materie corrosive");

            // Act
            AdrClass result1 = adrClassService.mapToEntity(dto1);
            AdrClass result2 = adrClassService.mapToEntity(dto2);

            // Assert - gli oggetti devono essere distinti (no shared state)
            assertThat(result1).isNotSameAs(result2);
            assertThat(result1.getClassCode()).isEqualTo("3");
            assertThat(result2.getClassCode()).isEqualTo("8");
        }

        /**
         * [TDD-RED] Verifica che {@code mapToEntity} lanci {@link IllegalArgumentException}
         * quando viene invocato con un DTO {@code null}.
         *
         * <p>
         * <b>QUESTO TEST E' PROGETTATO PER FALLIRE (Fase RED del TDD).</b><br>
         * Il metodo {@link AdrClassService#mapToEntity(AdrClassRequestDTO)} tenta immediatamente
         * di accedere a {@code dto.classCode()} e {@code dto.description()} senza alcun controllo
         * preventivo (riga 185-188 del sorgente). Se {@code dto} e' {@code null}, viene lanciata
         * una {@code NullPointerException} non gestita, anziche' un'eccezione semantica.
         * </p>
         * <p>
         * <b>Fix richiesto:</b> Aggiungere come prima istruzione:
         * {@code if (dto == null) throw new IllegalArgumentException("AdrClassRequestDTO cannot be null");}
         * </p>
         * <b>Output atteso:</b> {@link IllegalArgumentException} (non {@code NullPointerException}).
         */
        @Test
        @DisplayName("[TDD-RED] DTO null: mapToEntity(null) dovrebbe lanciare IllegalArgumentException (non NullPointerException)")
        void shouldThrowIllegalArgumentExceptionWhenDtoIsNull() {
            // Act & Assert
            assertThatThrownBy(() -> adrClassService.mapToEntity(null))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(NullPointerException.class);
        }

        /**
         * [TDD-RED] Verifica che {@code mapToEntity} lanci {@link IllegalArgumentException}
         * quando il DTO contiene un {@code classCode} null.
         *
         * <p>
         * <b>QUESTO TEST E' PROGETTATO PER FALLIRE (Fase RED del TDD).</b><br>
         * Il metodo esegue {@code adrClass.setClassCode(dto.classCode())} senza validare
         * che {@code dto.classCode()} non sia null. L'entita' risultante avrebbe
         * {@code classCode = null}, violando il vincolo {@code nullable = false} della colonna
         * e causando un'eccezione JPA non semantica al momento del {@code save()}.
         * La validazione dovrebbe avvenire nel Service, non al momento del persist.
         * </p>
         * <p>
         * <b>Fix richiesto:</b>
         * {@code if (dto.classCode() == null || dto.classCode().isBlank()) throw new IllegalArgumentException(...);}
         * </p>
         * <b>Output atteso:</b> {@link IllegalArgumentException} DURANTE il mapping.
         */
        @Test
        @DisplayName("[TDD-RED] classCode null nel DTO: mapToEntity dovrebbe lanciare IllegalArgumentException durante il mapping")
        void shouldThrowIllegalArgumentExceptionWhenDtoClassCodeIsNull() {
            // Arrange
            AdrClassRequestDTO dtoWithNullCode = new AdrClassRequestDTO(null, "Descrizione valida");

            // Act & Assert
            assertThatThrownBy(() -> adrClassService.mapToEntity(dtoWithNullCode))
                .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * [TDD-RED] Verifica che {@code mapToEntity} lanci {@link IllegalArgumentException}
         * quando il DTO contiene una {@code description} null.
         *
         * <p>
         * <b>QUESTO TEST E' PROGETTATO PER FALLIRE (Fase RED del TDD).</b><br>
         * Analoga vulnerabilita' al test precedente: {@code dto.description()} non viene
         * validato (riga 187 del sorgente). Un'entity con {@code description = null}
         * violerebbe il vincolo {@code nullable = false} della colonna {@code description}
         * al momento del persist, generando un'eccezione JPA non semantica.
         * </p>
         * <p>
         * <b>Fix richiesto:</b>
         * {@code if (dto.description() == null || dto.description().isBlank()) throw new IllegalArgumentException(...);}
         * </p>
         * <b>Output atteso:</b> {@link IllegalArgumentException} DURANTE il mapping.
         */
        @Test
        @DisplayName("[TDD-RED] description null nel DTO: mapToEntity dovrebbe lanciare IllegalArgumentException durante il mapping")
        void shouldThrowIllegalArgumentExceptionWhenDtoDescriptionIsNull() {
            // Arrange
            AdrClassRequestDTO dtoWithNullDescription = new AdrClassRequestDTO("3", null);

            // Act & Assert
            assertThatThrownBy(() -> adrClassService.mapToEntity(dtoWithNullDescription))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
