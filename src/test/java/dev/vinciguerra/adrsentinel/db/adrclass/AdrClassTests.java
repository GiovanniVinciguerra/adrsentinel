package dev.vinciguerra.adrsentinel.db.adrclass;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Suite di test unitari per l'entità JPA {@link AdrClass}.
 *
 * <p>
 * Questa classe verifica in isolamento completo (nessun contesto Spring, nessun database)
 * <b>ogni singolo metodo</b> dell'entità {@code AdrClass}, applicando un approccio
 * TDD difensivo (Zero-Trust / Spietato). Oltre agli Happy Path, vengono sistematicamente
 * coperti Failure Path, Edge Case e Boundary Value.
 * </p>
 * <ul>
 *   <li>Test in <b>Fase RED</b> deliberatamente progettati per fallire ed evidenziare
 *       vulnerabilità nel codice sorgente attuale.</li>
 * </ul>
 *
 * <h3>Stack Tecnologico:</h3>
 * <ul>
 *   <li>JUnit 5 (Jupiter) - orchestrazione dei test.</li>
 *   <li>AssertJ - asserzioni fluenti e leggibili.</li>
 *   <li>Java Reflection API - per invocazione diretta del metodo privato {@code normalize()}.</li>
 * </ul>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 * @see AdrClass
 */
class AdrClassTests {

    /**
     * Metodo helper che invoca tramite Java Reflection il metodo privato
     * {@code normalize()} di un'istanza {@link AdrClass}.
     * <p>
     * Simula il ciclo di vita JPA ({@code @PrePersist} / {@code @PreUpdate}) in isolamento
     * puro, senza avviare il contesto Spring o un container JPA reale.
     * </p>
     *
     * @param entity l'istanza di {@link AdrClass} su cui invocare la normalizzazione.
     * @throws Exception se la reflection fallisce per motivi di accesso o invocazione.
     */
    private void invokeNormalize(AdrClass entity) throws Exception {
        Method normalizeMethod = AdrClass.class.getDeclaredMethod("normalize");
        normalizeMethod.setAccessible(true);
        normalizeMethod.invoke(entity);
    }

    // =========================================================================
    // INNER CLASS: Getters e Setters
    // =========================================================================

    /**
     * Classe nested che verifica il comportamento di tutti i getter e setter
     * dell'entità {@link AdrClass}.
     * <p>
     * I getter e setter sono contratti pubblici dell'entità e devono essere verificati
     * per garantire la corretta assegnazione e lettura dei campi, specialmente
     * considerando che il campo {@code id} può essere {@code null} prima della persistenza
     * (stato JPA Transient).
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Getters e Setters")
    class GettersAndSettersTest {

        /** Istanza pulita di AdrClass creata prima di ogni test. */
        private AdrClass adrClass;

        /**
         * Setup di un'istanza pulita di {@link AdrClass} prima di ogni test.
         * Nessun mock è necessario: i getter/setter non hanno dipendenze esterne.
         */
        @BeforeEach
        void setUp() {
            adrClass = new AdrClass();
        }

        /**
         * Verifica che {@code getId()} restituisca {@code null} per un'entità
         * appena istanziata (stato JPA Transient), ovvero prima che il database
         * abbia assegnato un ID autogenerato.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code null}.
         * </p>
         */
        @Test
        @DisplayName("getId() deve restituire null per un'entità transient (pre-persist)")
        void shouldReturnNullIdForTransientEntity() {
            // Arrange: entità appena creata, non ancora persistita

            // Act & Assert
            assertThat(adrClass.getId()).isNull();
        }

        /**
         * Verifica che {@code setId(Long)} assegni correttamente l'ID e che
         * {@code getId()} lo restituisca invariato.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code 42L}.
         * </p>
         */
        @Test
        @DisplayName("setId() + getId() devono lavorare in coppia simmetrica")
        void shouldSetAndGetId() {
            // Arrange
            Long expectedId = 42L;

            // Act
            adrClass.setId(expectedId);

            // Assert
            assertThat(adrClass.getId()).isEqualTo(expectedId);
        }

        /**
         * Verifica che {@code setId(null)} sia accettato senza eccezioni, poiché un ID nullo
         * è lo stato legittimo di un'entità non ancora persistita (stato JPA Transient).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> nessuna eccezione; {@code getId()} restituisce {@code null}.
         * </p>
         */
        @Test
        @DisplayName("setId(null) deve essere accettato senza eccezioni (stato Transient valido)")
        void shouldAcceptNullId() {
            // Arrange
            adrClass.setId(100L);

            // Act & Assert
            assertDoesNotThrow(() -> adrClass.setId(null));
            assertThat(adrClass.getId()).isNull();
        }

        /**
         * Verifica che {@code setClassCode(String)} + {@code getClassCode()} funzionino
         * come coppia simmetrica con un classCode valido (Happy Path).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> il classCode assegnato viene restituito invariato.
         * </p>
         */
        @Test
        @DisplayName("setClassCode() + getClassCode() devono lavorare in coppia simmetrica")
        void shouldSetAndGetClassCode() {
            // Arrange
            String expectedCode = "3";

            // Act
            adrClass.setClassCode(expectedCode);

            // Assert
            assertThat(adrClass.getClassCode()).isEqualTo(expectedCode);
        }

        /**
         * Verifica che {@code setClassCode(null)} non lanci eccezioni a livello di setter.
         * Il null-check è responsabilità del Service Layer e del database.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> nessuna eccezione; {@code getClassCode()} restituisce {@code null}.
         * </p>
         */
        @Test
        @DisplayName("setClassCode(null) non deve lanciare eccezioni a livello di entity")
        void shouldAcceptNullClassCode() {
            // Arrange: nessuna pre-condizione particolare

            // Act & Assert
            assertDoesNotThrow(() -> adrClass.setClassCode(null));
            assertThat(adrClass.getClassCode()).isNull();
        }

        /**
         * Verifica che {@code setDescription(String)} + {@code getDescription()} funzionino
         * come coppia simmetrica con un valore valido (Happy Path).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> la description assegnata viene restituita invariata.
         * </p>
         */
        @Test
        @DisplayName("setDescription() + getDescription() devono lavorare in coppia simmetrica")
        void shouldSetAndGetDescription() {
            // Arrange
            String expectedDescription = "Liquidi infiammabili";

            // Act
            adrClass.setDescription(expectedDescription);

            // Assert
            assertThat(adrClass.getDescription()).isEqualTo(expectedDescription);
        }

        /**
         * Verifica che {@code setDescription(null)} non lanci eccezioni a livello di setter.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> nessuna eccezione; {@code getDescription()} restituisce {@code null}.
         * </p>
         */
        @Test
        @DisplayName("setDescription(null) non deve lanciare eccezioni a livello di entity")
        void shouldAcceptNullDescription() {
            // Arrange: nessuna pre-condizione particolare

            // Act & Assert
            assertDoesNotThrow(() -> adrClass.setDescription(null));
            assertThat(adrClass.getDescription()).isNull();
        }
    }

    // =========================================================================
    // INNER CLASS: normalize()
    // =========================================================================

    /**
     * Classe nested che verifica il comportamento del metodo privato {@code normalize()},
     * il callback JPA ({@code @PrePersist} / {@code @PreUpdate}) responsabile della
     * normalizzazione silente dei dati in input.
     * <p>
     * Questa è l'area più critica dell'entità per la User Experience (Tolerant Reader Pattern).
     * La verifica è effettuata tramite Java Reflection per simulare il comportamento del
     * container JPA in isolamento puro.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("normalize() — Callback JPA @PrePersist/@PreUpdate")
    class NormalizeTest {

        /**
         * Verifica l'Happy Path principale: un classCode con spazi laterali e lettere minuscole
         * deve essere trimmed e convertito in maiuscolo (es. "  1.4s  " -&gt; "1.4S").
         * <p>
         * <b>Mock:</b> nessuno. Invocazione tramite Reflection.<br>
         * <b>Output atteso:</b> {@code classCode = "1.4S"}, {@code description} invariata.
         * </p>
         */
        @Test
        @DisplayName("normalize() deve trimmare e uppercasare il classCode con spazi laterali e minuscole")
        void shouldTrimAndUppercaseClassCode() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("  1.4s  ");
            entity.setDescription("Materie esplosive");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getClassCode()).isEqualTo("1.4S");
            assertThat(entity.getDescription()).isEqualTo("Materie esplosive");
        }

        /**
         * Verifica che {@code normalize()} converti in maiuscolo il carattere finale minuscolo
         * del classCode (es. "1.4s" -&gt; "1.4S").
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code classCode = "1.4S"}.
         * </p>
         */
        @Test
        @DisplayName("normalize() deve uppercasare solo la lettera finale minuscola del classCode")
        void shouldUppercaseLowercaseSuffixInClassCode() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("1.4s");
            entity.setDescription("Gas");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getClassCode()).isEqualTo("1.4S");
        }

        /**
         * Verifica che {@code normalize()} non alteri un classCode già correttamente
         * formattato (idempotenza del lifecycle callback).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code classCode = "3"} invariato.
         * </p>
         */
        @Test
        @DisplayName("normalize() deve essere idempotente su un classCode già corretto")
        void shouldBeIdempotentOnCorrectClassCode() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("3");
            entity.setDescription("Liquidi infiammabili");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getClassCode()).isEqualTo("3");
        }

        /**
         * Verifica che {@code normalize()} non lanci eccezioni quando {@code classCode} è
         * {@code null} (branch null-safe nel sorgente: {@code if(classCode != null)}).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> nessuna eccezione; {@code classCode} rimane {@code null}.
         * </p>
         */
        @Test
        @DisplayName("normalize() non deve lanciare eccezioni se classCode è null")
        void shouldNotThrowWhenClassCodeIsNull() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode(null);
            entity.setDescription("Gas");

            // Act & Assert
            assertDoesNotThrow(() -> invokeNormalize(entity));
            assertThat(entity.getClassCode()).isNull();
        }

        /**
         * Verifica che {@code normalize()} non lanci eccezioni quando {@code description} è
         * {@code null} (branch null-safe nel sorgente: {@code if(description != null)}).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> nessuna eccezione; {@code description} rimane {@code null}.
         * </p>
         */
        @Test
        @DisplayName("normalize() non deve lanciare eccezioni se description è null")
        void shouldNotThrowWhenDescriptionIsNull() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("3");
            entity.setDescription(null);

            // Act & Assert
            assertDoesNotThrow(() -> invokeNormalize(entity));
            assertThat(entity.getDescription()).isNull();
        }

        /**
         * Verifica che {@code normalize()} sostituisca i ritorni a capo ({@code \n}) nella
         * description con spazi singoli (gestione "copia-incolla da documenti normativi").
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code description = "Materie tossiche per inalazione"}.
         * </p>
         */
        @Test
        @DisplayName("normalize() deve sostituire i newline nella description con spazi singoli")
        void shouldReplaceNewlinesInDescription() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("6");
            entity.setDescription("Materie tossiche\nper inalazione");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getDescription()).isEqualTo("Materie tossiche per inalazione");
        }

        /**
         * Verifica che {@code normalize()} sostituisca le sequenze CRLF ({@code \r\n})
         * nella description con spazi singoli.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code description = "Materie tossiche per inalazione"}.
         * </p>
         */
        @Test
        @DisplayName("normalize() deve sostituire CRLF (\\r\\n) nella description con uno spazio")
        void shouldReplaceCRLFInDescription() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("6");
            entity.setDescription("Materie tossiche\r\nper inalazione");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getDescription()).isEqualTo("Materie tossiche per inalazione");
        }

        /**
         * Verifica che {@code normalize()} sostituisca le tabulazioni ({@code \t}) nella
         * description con spazi singoli.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code description = "Materie tossiche per inalazione"}.
         * </p>
         */
        @Test
        @DisplayName("normalize() deve sostituire le tabulazioni nella description con spazi singoli")
        void shouldReplaceTabsInDescription() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("6");
            entity.setDescription("Materie tossiche\tper inalazione");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getDescription()).isEqualTo("Materie tossiche per inalazione");
        }

        /**
         * Verifica che {@code normalize()} collassi gli spazi multipli consecutivi nella
         * description in un singolo spazio (es. "Materie   esplosive" -&gt; "Materie esplosive").
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code description = "Materie esplosive"}.
         * </p>
         */
        @Test
        @DisplayName("normalize() deve collassare gli spazi multipli nella description in uno solo")
        void shouldCollapseMultipleSpacesInDescription() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("1");
            entity.setDescription("Materie   esplosive");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getDescription()).isEqualTo("Materie esplosive");
        }

        /**
         * Verifica che {@code normalize()} rimuova gli spazi laterali dalla description (trim).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code description = "Gas"}.
         * </p>
         */
        @Test
        @DisplayName("normalize() deve trimmare gli spazi laterali dalla description")
        void shouldTrimDescriptionPadding() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("2");
            entity.setDescription("   Gas   ");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getDescription()).isEqualTo("Gas");
        }

        /**
         * Verifica il worst-case: una description con ritorni a capo, tabulazioni,
         * spazi multipli e padding laterale viene completamente normalizzata in una
         * sola stringa pulita.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code description = "Materie tossiche e infettanti"}.
         * </p>
         */
        @Test
        @DisplayName("normalize() deve applicare l'intera pipeline di pulizia sulla description")
        void shouldApplyFullCleanupPipelineOnDescription() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("6");
            entity.setDescription("  Materie \t tossiche \n e   infettanti  \r\n  ");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getDescription()).isEqualTo("Materie tossiche e infettanti");
        }

        /**
         * Boundary Value: classCode a esattamente 4 caratteri (lunghezza massima consentita
         * dalla colonna JPA {@code length = 4}) — il trim non deve alterarlo.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code classCode = "1.4S"} (4 caratteri, invariato dopo trim+uppercase).
         * </p>
         */
        @Test
        @DisplayName("normalize() deve gestire correttamente un classCode al limite di 4 caratteri")
        void shouldHandleClassCodeAtMaxLengthBoundary() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("1.4s"); // 4 caratteri + lettera minuscola
            entity.setDescription("Gas");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getClassCode())
                    .isEqualTo("1.4S")
                    .hasSize(4);
        }

        /**
         * [RED-TEST] Verifica che {@code normalize()} rilevasse il caso di classCode blank-only
         * e lanciasse un'eccezione (o imponesse che il risultato sia non-blank).
         * <p>
         * <b>Vulnerabilità Rilevata:</b> Il metodo {@code normalize()} attuale esegue
         * {@code classCode.trim().toUpperCase()}. Se il classCode contiene solo spazi,
         * il risultato è una stringa vuota (""). Non viene lanciata alcuna eccezione, quindi
         * un'entità con classCode blank supera il lifecycle callback JPA e può tentare
         * di essere salvata violando il vincolo {@code nullable = false} sul DB.
         * </p>
         * <p>
         * <b>Correzione necessaria:</b> Aggiungere in {@code normalize()} un check
         * post-trim: {@code if(classCode.isBlank()) throw new IllegalStateException("classCode cannot be blank after normalization")}.
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso (comportamento CORRETTO da implementare):</b> {@code classCode} non blank.<br>
         * <b>Comportamento ATTUALE (difettoso):</b> nessuna eccezione, classCode diventa "".
         * </p>
         */
        @Test
        @DisplayName("[RED-TEST] normalize() dovrebbe rifiutare un classCode blank-only dopo il trim")
        void shouldThrowWhenClassCodeIsBlankOnlyAfterTrim_RED() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("   "); // solo spazi
            entity.setDescription("Materie esplosive");

            // Act
            assertThatThrownBy(() -> invokeNormalize(entity))
	            .isInstanceOf(InvocationTargetException.class)
	        	.hasCauseInstanceOf(IllegalStateException.class);
        }

        /**
         * [RED-TEST] Verifica che {@code normalize()} rilevasse il caso di description blank-only
         * e lanciasse un'eccezione (o imponesse che il risultato sia non-blank).
         * <p>
         * <b>Vulnerabilità Rilevata:</b> Il metodo {@code normalize()} esegue
         * {@code description.trim()} senza verificare se il risultato è una stringa vuota.
         * Una description di soli spazi viene normalizzata a "", che è invalida per la colonna
         * DB ({@code nullable = false}), ma non viene intercettata a livello applicativo.
         * </p>
         * <p>
         * <b>Correzione necessaria:</b> Aggiungere post-trim un check:
         * {@code if(description.isBlank()) throw new IllegalStateException("description cannot be blank after normalization")}.
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso (comportamento CORRETTO):</b> {@code description} non blank.<br>
         * <b>Comportamento ATTUALE (difettoso):</b> nessuna eccezione, description diventa "".
         * </p>
         */
        @Test
        @DisplayName("[RED-TEST] normalize() dovrebbe rifiutare una description blank-only dopo il trim")
        void shouldThrowWhenDescriptionIsBlankOnlyAfterTrim_RED() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("3");
            entity.setDescription("   "); // solo spazi

            // Act            
            assertThatThrownBy(() -> invokeNormalize(entity))
            	.isInstanceOf(InvocationTargetException.class)
            	.hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // INNER CLASS: equals()
    // =========================================================================

    /**
     * Classe nested che verifica il contratto completo di {@link AdrClass#equals(Object)}.
     * <p>
     * L'uguaglianza è basata esclusivamente sul {@code classCode} (Business Key naturale).
     * Questa è la logica più critica per la corretta gestione nelle cache (Caffeine) e
     * nelle collezioni Java ({@code HashSet}, {@code List}).
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("equals()")
    class EqualsTest {

        /**
         * Verifica la riflessività: un oggetto deve essere uguale a se stesso
         * (contratto fondamentale di {@link Object#equals}).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code true}.
         * </p>
         */
        @Test
        @DisplayName("equals() deve essere riflessivo: un oggetto è uguale a se stesso")
        void shouldBeReflexive() {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("3");

            // Act & Assert
            assertThat(entity).isEqualTo(entity);
        }

        /**
         * Verifica la simmetria: due istanze con lo stesso {@code classCode} ma
         * ID diversi (o nulli) devono essere considerate uguali.
         * <p>
         * Caso tipico: un'entità in cache (senza ID) e un'entità appena letta dal DB (con ID).
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code true} in entrambe le direzioni.
         * </p>
         */
        @Test
        @DisplayName("equals() deve essere simmetrico: due entità con lo stesso classCode sono uguali")
        void shouldBeSymmetricForSameClassCode() {
            // Arrange
            AdrClass entityA = new AdrClass();
            entityA.setId(1L);
            entityA.setClassCode("3");

            AdrClass entityB = new AdrClass();
            entityB.setId(2L); // ID diverso
            entityB.setClassCode("3"); // stesso classCode

            // Act & Assert
            assertThat(entityA).isEqualTo(entityB);
            assertThat(entityB).isEqualTo(entityA);
        }

        /**
         * Verifica che due entità con {@code classCode} diversi non siano uguali.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code false}.
         * </p>
         */
        @Test
        @DisplayName("equals() deve restituire false per entità con classCode diversi")
        void shouldReturnFalseForDifferentClassCode() {
            // Arrange
            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");

            AdrClass class8 = new AdrClass();
            class8.setClassCode("8");

            // Act & Assert
            assertThat(class3).isNotEqualTo(class8);
        }

        /**
         * Verifica che {@code equals(null)} restituisca {@code false} senza lanciare
         * {@code NullPointerException} (contratto Object).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code false}.
         * </p>
         */
        @Test
        @DisplayName("equals(null) deve restituire false senza lanciare NullPointerException")
        void shouldReturnFalseWhenComparedToNull() {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("3");

            // Act & Assert
            assertThat(entity.equals(null)).isFalse();
        }

        /**
         * Verifica che {@code equals(Object)} restituisca {@code false} quando l'oggetto
         * confrontato è di un tipo diverso (es. una {@code String}).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code false}.
         * </p>
         */
        @Test
        @DisplayName("equals() deve restituire false quando confrontato con un oggetto di tipo diverso")
        void shouldReturnFalseForDifferentType() {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("3");

            // Act & Assert
            assertThat(entity.equals("3")).isFalse();
        }

        /**
         * Verifica che un'entità con {@code classCode} valorizzato non sia uguale a
         * un'entità con {@code classCode = null}.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code false}.
         * </p>
         */
        @Test
        @DisplayName("equals() deve restituire false se un classCode è null e l'altro no")
        void shouldReturnFalseWhenOneClassCodeIsNull() {
            // Arrange
            AdrClass entityWithCode = new AdrClass();
            entityWithCode.setClassCode("3");

            AdrClass entityNullCode = new AdrClass();
            entityNullCode.setClassCode(null);

            // Act & Assert
            assertThat(entityWithCode.equals(entityNullCode)).isFalse();
            assertThat(entityNullCode.equals(entityWithCode)).isFalse();
        }

        /**
         * [RED-TEST] Verifica che due istanze con {@code classCode = null} NON siano
         * considerate uguali (il classCode null indica un'entità in stato invalido).
         * <p>
         * <b>Vulnerabilità Rilevata:</b> Il metodo {@code equals()} attuale usa
         * {@code Objects.equals(classCode, other.classCode)}. Se entrambe le istanze hanno
         * {@code classCode = null}, {@code Objects.equals(null, null)} restituisce {@code true}.
         * Due entità in stato invalido (classCode null) vengono quindi considerate "la stessa
         * entità", causando comportamenti imprevedibili nelle cache.
         * </p>
         * <p>
         * <b>Correzione necessaria:</b> Aggiungere un early return {@code false} se
         * {@code this.classCode == null} o {@code other.classCode == null}.
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso (comportamento CORRETTO):</b> {@code false}.<br>
         * <b>Comportamento ATTUALE (difettoso):</b> {@code true} (due null sono "uguali").
         * </p>
         */
        @Test
        @DisplayName("[RED-TEST] equals() non deve considerare uguali due entità con classCode null")
        void shouldReturnFalseWhenBothClassCodesAreNull_RED() {
            // Arrange
            AdrClass entityA = new AdrClass();
            entityA.setClassCode(null);

            AdrClass entityB = new AdrClass();
            entityB.setClassCode(null);

            // Act & Assert — FASE RED
            // Con il codice attuale, Objects.equals(null, null) == true → equals() restituisce true.
            // Ci si aspetta false: due entità senza Business Key non possono essere identiche.
            assertThat(entityA.equals(entityB))
                    .as("[RED-TEST] Due entità con classCode null non devono essere considerate uguali")
                    .isFalse();
        }

        /**
         * Verifica l'integrazione con {@link HashSet}: due entità con lo stesso
         * {@code classCode} non devono generare duplicati in un Set.
         * <p>
         * Fondamentale per il corretto funzionamento della cache (Caffeine) e per
         * l'invalidazione delle entry tramite {@code Set.remove(value)}.
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> il Set contiene un solo elemento.
         * </p>
         */
        @Test
        @DisplayName("equals() + hashCode() devono prevenire duplicati in un HashSet (critico per la cache)")
        void shouldPreventDuplicatesInHashSet() {
            // Arrange
            AdrClass entityA = new AdrClass();
            entityA.setId(1L);
            entityA.setClassCode("3");
            entityA.setDescription("Liquidi infiammabili");

            AdrClass entityB = new AdrClass();
            entityB.setId(2L);
            entityB.setClassCode("3");
            entityB.setDescription("Liquidi infiammabili - aggiornato");

            Set<AdrClass> set = new HashSet<>();

            // Act
            set.add(entityA);
            set.add(entityB);

            // Assert
            assertThat(set).hasSize(1);
        }
    }

    // =========================================================================
    // INNER CLASS: hashCode()
    // =========================================================================

    /**
     * Classe nested che verifica il contratto di {@link AdrClass#hashCode()}.
     * <p>
     * L'hash code è basato esclusivamente sul {@code classCode}, in modo coerente con
     * {@code equals()}. Questa coerenza è fondamentale per il corretto funzionamento
     * in strutture dati basate su hash (es. {@code HashMap}, {@code HashSet}).
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("hashCode()")
    class HashCodeTest {

        /**
         * Verifica la consistenza: due istanze con lo stesso {@code classCode} devono
         * produrre lo stesso hash code (contratto del metodo {@code hashCode}).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> hash codes uguali.
         * </p>
         */
        @Test
        @DisplayName("hashCode() deve essere consistente: due oggetti uguali hanno lo stesso hash")
        void shouldBeConsistentForEqualObjects() {
            // Arrange
            AdrClass entityA = new AdrClass();
            entityA.setClassCode("3");

            AdrClass entityB = new AdrClass();
            entityB.setClassCode("3");

            // Act & Assert
            assertThat(entityA.hashCode()).isEqualTo(entityB.hashCode());
        }

        /**
         * Verifica che due istanze con {@code classCode} diversi producano hash code diversi.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> hash codes diversi.
         * </p>
         */
        @Test
        @DisplayName("hashCode() deve produrre hash diversi per classCode diversi")
        void shouldProduceDifferentHashForDifferentClassCode() {
            // Arrange
            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");

            AdrClass class8 = new AdrClass();
            class8.setClassCode("8");

            // Act & Assert
            assertThat(class3.hashCode()).isNotEqualTo(class8.hashCode());
        }

        /**
         * Verifica che {@code hashCode()} non lanci eccezioni quando {@code classCode} è
         * {@code null} ({@code Objects.hash(null)} restituisce un valore costante della JVM).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> nessuna eccezione.
         * </p>
         */
        @Test
        @DisplayName("hashCode() non deve lanciare eccezioni quando classCode è null")
        void shouldNotThrowWhenClassCodeIsNull() {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode(null);

            // Act & Assert
            assertDoesNotThrow(() -> entity.hashCode());
        }

        /**
         * Verifica la stabilità dell'hash code: invocazioni ripetute sullo stesso oggetto
         * devono restituire lo stesso valore.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> hash code stabile tra multiple invocazioni.
         * </p>
         */
        @Test
        @DisplayName("hashCode() deve essere stabile tra invocazioni multiple sullo stesso oggetto")
        void shouldBeStableAcrossMultipleInvocations() {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("4.1");

            // Act
            int firstHash = entity.hashCode();
            int secondHash = entity.hashCode();
            int thirdHash = entity.hashCode();

            // Assert
            assertThat(firstHash).isEqualTo(secondHash).isEqualTo(thirdHash);
        }
    }

    // =========================================================================
    // INNER CLASS: compareTo()
    // =========================================================================

    /**
     * Classe nested che verifica il contratto di {@link AdrClass#compareTo(AdrClass)}.
     * <p>
     * Il metodo abilita l'ordinamento naturale delle entità nelle collezioni ordinate
     * (es. {@link TreeSet}). L'ordinamento è delegato a {@code String.compareTo()}.
     * </p>
     * <p>
     * <b>ATTENZIONE — Vulnerabilità critica:</b> Il metodo {@code compareTo()} chiama
     * direttamente {@code classCode.compareTo(classB.classCode)} senza null-check su
     * {@code this.classCode} né su {@code classB.classCode}. Questo causa una
     * {@code NullPointerException} runtime se una delle due entità ha {@code classCode = null}.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("compareTo()")
    class CompareToTest {

        /**
         * Verifica l'Happy Path: "3" viene prima di "8" in ordine lessicografico.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> valore negativo.
         * </p>
         */
        @Test
        @DisplayName("compareTo() deve restituire un valore negativo se this classCode precede l'altro")
        void shouldReturnNegativeWhenThisCodePrecedesOther() {
            // Arrange
            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");

            AdrClass class8 = new AdrClass();
            class8.setClassCode("8");

            // Act
            int result = class3.compareTo(class8);

            // Assert
            assertThat(result).isNegative();
        }

        /**
         * Verifica che {@code compareTo()} restituisca un valore positivo quando
         * il classCode di {@code this} è lessicograficamente successivo.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> valore positivo.
         * </p>
         */
        @Test
        @DisplayName("compareTo() deve restituire un valore positivo se this classCode segue l'altro")
        void shouldReturnPositiveWhenThisCodeFollowsOther() {
            // Arrange
            AdrClass class8 = new AdrClass();
            class8.setClassCode("8");

            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");

            // Act
            int result = class8.compareTo(class3);

            // Assert
            assertThat(result).isPositive();
        }

        /**
         * Verifica che {@code compareTo()} restituisca zero quando i classCode sono identici.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code 0}.
         * </p>
         */
        @Test
        @DisplayName("compareTo() deve restituire zero per entità con lo stesso classCode")
        void shouldReturnZeroForEqualClassCodes() {
            // Arrange
            AdrClass entityA = new AdrClass();
            entityA.setClassCode("4.1");

            AdrClass entityB = new AdrClass();
            entityB.setClassCode("4.1");

            // Act
            int result = entityA.compareTo(entityB);

            // Assert
            assertThat(result).isZero();
        }

        /**
         * Verifica la coerenza con {@code equals()}: se {@code compareTo()} restituisce zero,
         * {@code equals()} deve restituire {@code true}.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code compareTo() == 0} implica {@code equals() == true}.
         * </p>
         */
        @Test
        @DisplayName("compareTo() == 0 deve essere consistente con equals() == true")
        void shouldBeConsistentWithEquals() {
            // Arrange
            AdrClass entityA = new AdrClass();
            entityA.setClassCode("6.1");

            AdrClass entityB = new AdrClass();
            entityB.setClassCode("6.1");

            // Act
            int compareResult = entityA.compareTo(entityB);
            boolean equalsResult = entityA.equals(entityB);

            // Assert
            assertThat(compareResult).isZero();
            assertThat(equalsResult).isTrue();
        }

        /**
         * Verifica l'ordinamento in un {@link TreeSet}: le entità devono essere
         * ordinate correttamente in base al classCode.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> ordine ["1", "3", "4.1", "6.1", "8"].
         * </p>
         */
        @Test
        @DisplayName("compareTo() deve abilitare l'ordinamento corretto in un TreeSet")
        void shouldEnableCorrectOrderingInTreeSet() {
            // Arrange
            AdrClass class8 = new AdrClass();
            class8.setClassCode("8");
            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");
            AdrClass class41 = new AdrClass();
            class41.setClassCode("4.1");
            AdrClass class1 = new AdrClass();
            class1.setClassCode("1");
            AdrClass class61 = new AdrClass();
            class61.setClassCode("6.1");

            TreeSet<AdrClass> sortedSet = new TreeSet<>();

            // Act
            sortedSet.add(class8);
            sortedSet.add(class3);
            sortedSet.add(class41);
            sortedSet.add(class1);
            sortedSet.add(class61);

            // Assert
            List<String> sortedCodes = sortedSet.stream()
                    .map(AdrClass::getClassCode)
                    .toList();
            assertThat(sortedCodes).containsExactly("1", "3", "4.1", "6.1", "8");
        }

        /**
         * Verifica l'ordinamento con {@code Collections.sort()} su una lista di entità.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> lista ordinata per classCode in ordine crescente.
         * </p>
         */
        @Test
        @DisplayName("compareTo() deve abilitare l'ordinamento corretto con Collections.sort()")
        void shouldEnableCorrectOrderingWithCollectionsSort() {
            // Arrange
            AdrClass class9 = new AdrClass();
            class9.setClassCode("9");
            AdrClass class2 = new AdrClass();
            class2.setClassCode("2");
            AdrClass class51 = new AdrClass();
            class51.setClassCode("5.1");

            List<AdrClass> list = new ArrayList<>(List.of(class9, class2, class51));

            // Act
            Collections.sort(list);

            // Assert
            assertThat(list).extracting(AdrClass::getClassCode)
                    .containsExactly("2", "5.1", "9");
        }

        /**
         * [RED-TEST] Verifica che {@code compareTo()} NON lanci una {@code NullPointerException}
         * non gestita quando {@code this.classCode} è {@code null}.
         * <p>
         * <b>Vulnerabilità Critica:</b> Il metodo attuale esegue
         * {@code classCode.compareTo(classB.classCode)} senza null-check su {@code this.classCode}.
         * Un'entità in stato Transient (non ancora persistita) può avere {@code classCode = null}.
         * Se questa entità viene inserita in un {@link TreeSet} o se {@code compareTo()} viene
         * invocato prima del persist, l'applicazione crasha con NPE non gestita.
         * </p>
         * <p>
         * <b>Correzione necessaria:</b> Aggiungere un null-check su {@code this.classCode}
         * in {@code compareTo()}, ad esempio: {@code if(this.classCode == null) throw new
         * IllegalStateException("classCode must not be null for comparison")}.
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso (comportamento CORRETTO):</b> nessuna NPE non gestita.<br>
         * <b>Comportamento ATTUALE (difettoso):</b> {@code NullPointerException}.
         * </p>
         */
        @Test
        @DisplayName("[RED-TEST] compareTo() non deve lanciare NPE non gestita se this.classCode è null")
        void shouldNotThrowNPEWhenThisClassCodeIsNull_RED() {
            // Arrange
            AdrClass entityWithNullCode = new AdrClass();
            entityWithNullCode.setClassCode(null);

            AdrClass entityWithCode = new AdrClass();
            entityWithCode.setClassCode("3");
            
            // Act & Assert — FASE RED
            // Il codice attuale lancia NullPointerException perché chiama null.compareTo(...).
            assertThatThrownBy(() -> entityWithNullCode.compareTo(entityWithCode))
            	.isInstanceOf(IllegalStateException.class);
        }

        /**
         * [RED-TEST] Verifica che {@code compareTo()} NON lanci una {@code NullPointerException}
         * non gestita quando {@code classB.classCode} è {@code null}.
         * <p>
         * <b>Vulnerabilità Critica:</b> {@code String.compareTo(null)} lancia
         * {@code NullPointerException}. Anche il classCode del parametro può essere in
         * stato invalido prima della persistenza.
         * </p>
         * <p>
         * <b>Correzione necessaria:</b> Aggiungere null-check su entrambi i classCode
         * prima del confronto lessicografico.
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso (comportamento corretto):</b> nessuna NPE.<br>
         * <b>Comportamento ATTUALE (difettoso):</b> {@code NullPointerException}.
         * </p>
         */
        @Test
        @DisplayName("[RED-TEST] compareTo() non deve lanciare NPE non gestita se classB.classCode è null")
        void shouldNotThrowNPEWhenOtherClassCodeIsNull_RED() {
            // Arrange
            AdrClass entityWithCode = new AdrClass();
            entityWithCode.setClassCode("3");

            AdrClass entityWithNullCode = new AdrClass();
            entityWithNullCode.setClassCode(null);
            
            // Act & Assert — FASE RED	
            assertThatThrownBy(() -> entityWithCode.compareTo(entityWithNullCode))
            	.isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // INNER CLASS: toString()
    // =========================================================================

    /**
     * Classe nested che verifica il metodo {@link AdrClass#toString()}.
     * <p>
     * Il metodo restituisce una rappresentazione testuale dell'entità contenente
     * id, classCode e description. È utilizzato principalmente per il logging e
     * il debugging.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        /**
         * Verifica l'Happy Path: la stringa prodotta da {@code toString()} contiene
         * tutte le informazioni principali dell'entità nel formato atteso.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> stringa contenente id, classCode e description.
         * </p>
         */
        @Test
        @DisplayName("toString() deve contenere id, classCode e description dell'entità")
        void shouldContainAllFields() {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setId(1L);
            entity.setClassCode("3");
            entity.setDescription("Liquidi infiammabili");

            // Act
            String result = entity.toString();

            // Assert
            assertThat(result)
                    .contains("AdrClass [id=1")
                    .contains("classCode=3")
                    .contains("description=Liquidi infiammabili");
        }

        /**
         * Verifica che {@code toString()} non lanci eccezioni quando tutti i campi
         * sono {@code null} (entità appena istanziata, stato Transient).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> nessuna eccezione; stringa contenente "null".
         * </p>
         */
        @Test
        @DisplayName("toString() non deve lanciare eccezioni quando tutti i campi sono null")
        void shouldNotThrowWhenAllFieldsAreNull() {
            // Arrange
            AdrClass entity = new AdrClass(); // tutti i campi sono null

            // Act & Assert
            assertDoesNotThrow(() -> entity.toString());
            assertThat(entity.toString())
                    .contains("AdrClass [id=")
                    .contains("null");
        }

        /**
         * Verifica che il formato di {@code toString()} corrisponda esattamente al pattern
         * definito dallo {@code StringBuilder} nel codice sorgente.
         * Pattern atteso: {@code "AdrClass [id=X, classCode=Y, description=Z]"}.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> stringa che corrisponde al pattern esatto.
         * </p>
         */
        @Test
        @DisplayName("toString() deve produrre una stringa nel formato 'AdrClass [id=X, classCode=Y, description=Z]'")
        void shouldMatchExpectedFormat() {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setId(7L);
            entity.setClassCode("8");
            entity.setDescription("Materie corrosive");

            // Act
            String result = entity.toString();

            // Assert
            assertThat(result).isEqualTo("AdrClass [id=7, classCode=8, description=Materie corrosive]");
        }

        /**
         * Verifica che {@code toString()} produca una stringa coerente con un classCode
         * al massimo della lunghezza consentita (4 caratteri).
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> stringa contenente "classCode=1.4S".
         * </p>
         */
        @Test
        @DisplayName("toString() deve gestire correttamente un classCode di 4 caratteri")
        void shouldHandleMaxLengthClassCodeInToString() {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setId(3L);
            entity.setClassCode("1.4S");
            entity.setDescription("Materie esplosive sussidiarie");

            // Act
            String result = entity.toString();

            // Assert
            assertThat(result).contains("classCode=1.4S");
        }
    }

    // =========================================================================
    // INNER CLASS: Scenari Composti
    // =========================================================================

    /**
     * Classe nested che verifica comportamenti end-to-end dell'entità {@link AdrClass}
     * combinando più metodi (normalizzazione + equals + hashCode + compareTo) per
     * simulare scenari realistici del dominio ADR.
     * <p>
     * Questi test coprono flussi compositi che potrebbero verificarsi nell'applicazione
     * reale, come il salvataggio di un'entità con dati "sporchi" e il suo successivo
     * confronto in una cache o in una collezione ordinata.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Scenari Composti (Normalizzazione + Uguaglianza + Ordinamento)")
    class ComposedScenariosTest {

        /**
         * Verifica che dopo la normalizzazione, due entità con lo stesso classCode
         * (ma con formati diversi in input, es. "  1.4s  " e "1.4S") siano considerate
         * uguali da {@code equals()} e producano lo stesso hash code.
         * <p>
         * Simula il caso in cui un operatore inserisce un classCode non normalizzato
         * che, dopo il lifecycle JPA, deve essere identico a quello già in cache.
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code equals() == true} e {@code hashCode()} uguali
         * dopo la normalizzazione di entrambe le entità.
         * </p>
         */
        @Test
        @DisplayName("Dopo normalize(), due entità con classCode equivalenti devono essere equals e avere lo stesso hashCode")
        void shouldBeEqualAndSameHashAfterNormalization() throws Exception {
            // Arrange
            AdrClass entityDirty = new AdrClass();
            entityDirty.setClassCode("  1.4s  ");
            entityDirty.setDescription("  Materie esplosive  ");

            AdrClass entityClean = new AdrClass();
            entityClean.setClassCode("1.4S");
            entityClean.setDescription("Materie esplosive");

            // Act: simula il ciclo di vita JPA
            invokeNormalize(entityDirty);

            // Assert
            assertThat(entityDirty).isEqualTo(entityClean);
            assertThat(entityDirty.hashCode()).isEqualTo(entityClean.hashCode());
        }

        /**
         * Verifica che un'entità normalizzata possa essere rimossa correttamente da una
         * {@link List} (pattern usato in {@code AbstractGenericService.deleteFromCache()}).
         * <p>
         * Il corretto funzionamento di {@code list.remove(value)} dipende da
         * {@code equals()} basato sulla Business Key.
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> la lista risultante non contiene l'entità rimossa.
         * </p>
         */
        @Test
        @DisplayName("Un'entità normalizzata deve poter essere rimossa da una List (simulazione cache delete)")
        void shouldBeRemovableFromListAfterNormalization() throws Exception {
            // Arrange
            AdrClass entityInCache = new AdrClass();
            entityInCache.setId(5L);
            entityInCache.setClassCode("3");
            entityInCache.setDescription("Liquidi infiammabili");

            AdrClass entityFromDirtyInput = new AdrClass();
            entityFromDirtyInput.setClassCode("  3  ");
            entityFromDirtyInput.setDescription("Liquidi infiammabili");

            invokeNormalize(entityFromDirtyInput); // simula @PrePersist

            List<AdrClass> cacheList = new ArrayList<>();
            cacheList.add(entityInCache);

            // Act: simula la logica di deleteFromCache in AbstractGenericService
            boolean removed = cacheList.remove(entityFromDirtyInput);

            // Assert
            assertThat(removed).isTrue();
            assertThat(cacheList).isEmpty();
        }

        /**
         * Boundary Value sul classCode di 1 carattere (il caso più corto consentito,
         * es. "3" per Liquidi Infiammabili): la normalizzazione non deve alterarlo.
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code classCode = "3"} invariato dopo normalize().
         * </p>
         */
        @Test
        @DisplayName("Boundary: classCode di un singolo carattere deve essere gestito correttamente da normalize()")
        void shouldHandleSingleCharClassCodeBoundary() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("3");
            entity.setDescription("Gas");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getClassCode())
                    .isEqualTo("3")
                    .hasSize(1);
        }

        /**
         * Boundary Value: description minima accettabile di 3 caratteri ("Gas") gestita
         * correttamente da {@code normalize()} senza alterazioni.
         * <p>
         * In base alla documentazione del campo: "La lunghezza minima di 3 caratteri
         * accoglie la classe con la descrizione più corta in assoluto ('Gas')".
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso:</b> {@code description = "Gas"} invariata.
         * </p>
         */
        @Test
        @DisplayName("Boundary: description minima di 3 caratteri ('Gas') deve essere gestita correttamente")
        void shouldHandleMinimumDescriptionLengthBoundary() throws Exception {
            // Arrange
            AdrClass entity = new AdrClass();
            entity.setClassCode("2");
            entity.setDescription("Gas");

            // Act
            invokeNormalize(entity);

            // Assert
            assertThat(entity.getDescription())
                    .isEqualTo("Gas")
                    .hasSize(3);
        }

        /**
         * [RED-TEST] Verifica che l'inserimento di un'entità con {@code classCode = null}
         * in un {@link TreeSet} non causi una {@code NullPointerException} non gestita.
         * <p>
         * <b>Vulnerabilità Critica (comportamento composito):</b> Quando un'entità con
         * {@code classCode = null} viene inserita in un {@link TreeSet}, Java invoca
         * automaticamente {@code compareTo()} per determinare la posizione. Il metodo
         * attuale propagherà una {@code NullPointerException} perché
         * {@code null.compareTo(...)} non è invocabile.
         * </p>
         * <p>
         * <b>Impatto:</b> Se il Service Layer mantiene una lista ordinata di
         * {@link AdrClass} e un'entità in stato Transient viene inserita per errore,
         * l'intera operazione crasha in modo non gestito.
         * </p>
         * <p>
         * <b>Mock:</b> nessuno.<br>
         * <b>Output atteso (CORRETTO):</b> nessuna NPE non gestita.<br>
         * <b>Comportamento ATTUALE (difettoso):</b> {@code NullPointerException}.
         * </p>
         */
        @Test
        @DisplayName("[RED-TEST] Inserire un'entità con classCode null in un TreeSet non deve causare NPE non gestita")
        void shouldNotCauseNPEWhenInsertingNullClassCodeInTreeSet_RED() {
            // Arrange
            AdrClass entityWithCode = new AdrClass();
            entityWithCode.setClassCode("3");

            AdrClass entityNullCode = new AdrClass();
            entityNullCode.setClassCode(null);

            TreeSet<AdrClass> sortedSet = new TreeSet<>();
            sortedSet.add(entityWithCode);
            
            // Act & Assert — FASE RED
            assertThatThrownBy(() -> sortedSet.add(entityNullCode))
            	.isInstanceOf(IllegalStateException.class);
        }
    }
}
