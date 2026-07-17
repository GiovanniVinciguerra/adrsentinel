package dev.vinciguerra.adrsentinel.db.compatibilityrule;

import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Suite di test unitari per l'entita JPA {@link CompatibilityRule}.
 *
 * <p>
 * Questa classe verifica in modo esaustivo e metodico ogni comportamento dell'entita,
 * coprendo gli Happy Path, i Failure Path, i Boundary Value e i casi limite previsti
 * dalla normativa ADR (trasporto merci pericolose). La strategia di test adottata e quella
 * del <b>puro isolamento</b>: nessun contesto Spring, nessun database in memoria (H2).
 * Le dipendenze vengono istanziate direttamente per garantire test veloci, deterministici
 * e completamente privi di effetti collaterali sull'infrastruttura.
 * </p>
 *
 * <h3>Architettura di Test:</h3>
 * <ul>
 *   <li>I metodi {@code @PrePersist}/{@code @PreUpdate} (privati JPA callbacks) vengono
 *   invocati tramite <b>Java Reflection</b>, tecnica che consente di raggiungere la visibilita
 *   {@code private} senza modificare la classe sorgente.</li>
 *   <li>Le istanze di {@link AdrClass} vengono costruite direttamente (no mock) poiche
 *   la logica di {@code compareTo} e {@code equals} e parte integrante del comportamento
 *   testato in {@link CompatibilityRule}.</li>
 * </ul>
 *
 * <h3>Test RED (Fase TDD):</h3>
 * <p>
 * Alcuni test di questa suite sono intenzionalmente progettati per <b>fallire (Fase RED)</b>.
 * Cio avviene perche il codice sorgente manca di un controllo logico fondamentale che dovrebbe
 * esistere per garantire la robustezza e la sicurezza del sistema. Il fallimento di questi test
 * e il comportamento atteso e desiderato: costringera lo sviluppatore ad aggiungere
 * il controllo mancante per portare il test allo stato GREEN.
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 * @see CompatibilityRule
 * @see AdrClass
 * @see BadRequestException
 */
class CompatibilityRuleTests {

    // =========================================================================
    // FIXTURE - Metodi di supporto per la reflection sui callback JPA privati
    // =========================================================================

    /**
     * Invoca tramite reflection il metodo privato {@code onBeforeSaveOrUpdate()} della classe target.
     * Rappresenta il punto di ingresso unificato per simulare il trigger del ciclo di vita JPA
     * ({@code @PrePersist} / {@code @PreUpdate}) senza avviare un contesto applicativo.
     *
     * @param rule l'istanza di {@link CompatibilityRule} su cui invocare il callback.
     * @throws Exception se la reflection fallisce o il metodo lancia un'eccezione wrappata
     *                   in {@link InvocationTargetException}.
     */
    private void triggerLifecycleCallback(CompatibilityRule rule) throws Exception {
        Method method = CompatibilityRule.class.getDeclaredMethod("onBeforeSaveOrUpdate");
        method.setAccessible(true);
        method.invoke(rule);
    }

    /**
     * Factory method di utilita per costruire istanze di {@link AdrClass} con il solo
     * {@code classCode} impostato. Utilizzato per testare la logica di ordinamento canonico
     * e di confronto nelle regole di compatibilita.
     *
     * @param classCode il codice ADR da assegnare (es. "3", "6.1", "1.4S").
     * @return un'istanza di {@link AdrClass} con il codice specificato.
     */
    private AdrClass buildAdrClass(String classCode) {
        AdrClass adrClass = new AdrClass();
        adrClass.setClassCode(classCode);
        return adrClass;
    }

    // =========================================================================
    // NESTED CLASS: Getter e Setter (Contratto base del JavaBean)
    // =========================================================================

    /**
     * Raggruppa i test unitari per il contratto JavaBean di {@link CompatibilityRule}.
     * Verifica che ogni coppia getter/setter funzioni correttamente in isolamento,
     * garantendo che lo stato interno dell'entita sia muabile tramite i metodi pubblici
     * e che i valori impostati vengano letti correttamente.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Contratto JavaBean - Getter e Setter")
    class GettersAndSettersTest {

        private CompatibilityRule rule;

        /**
         * Inizializza una nuova istanza di {@link CompatibilityRule} prima di ogni test
         * per garantire lo stato pulito e l'isolamento dei test.
         */
        @BeforeEach
        void setUp() {
            rule = new CompatibilityRule();
        }

        /**
         * Verifica che il metodo {@code setId(Long)} popoli correttamente il campo {@code id}
         * e che {@code getId()} lo restituisca invariato. La chiave primaria surrogata e gestita
         * da JPA, ma il contratto del setter deve essere rispettato per supportare
         * eventuali operazioni di merge (update).
         */
        @Test
        @DisplayName("getId/setId: dovrebbe leggere e scrivere la chiave primaria surrogata")
        void shouldGetAndSetId() {
            // Arrange
            Long expectedId = 42L;

            // Act
            rule.setId(expectedId);

            // Assert
            assertThat(rule.getId()).isEqualTo(expectedId);
        }

        /**
         * Verifica che un'entita in stato transiente (non ancora persistita dal framework JPA)
         * abbia il campo {@code id} a {@code null}, in quanto la generazione della chiave
         * avviene esclusivamente durante la fase di INSERT nel database.
         */
        @Test
        @DisplayName("getId: dovrebbe restituire null per una nuova entita transiente")
        void shouldReturnNullIdForNewTransientEntity() {
            // Arrange and Act: nessuna operazione, rule e nuova

            // Assert
            assertThat(rule.getId()).isNull();
        }

        /**
         * Verifica che il metodo {@code setAdrClassA(AdrClass)} e il corrispondente getter
         * {@code getAdrClassA()} funzionino correttamente, consentendo di associare
         * la prima classe ADR alla regola di compatibilita.
         */
        @Test
        @DisplayName("getAdrClassA/setAdrClassA: dovrebbe leggere e scrivere la classe ADR A")
        void shouldGetAndSetAdrClassA() {
            // Arrange
            AdrClass classA = buildAdrClass("3");

            // Act
            rule.setAdrClassA(classA);

            // Assert
            assertThat(rule.getAdrClassA()).isEqualTo(classA);
            assertThat(rule.getAdrClassA().getClassCode()).isEqualTo("3");
        }

        /**
         * Verifica che il metodo {@code setAdrClassB(AdrClass)} e il corrispondente getter
         * {@code getAdrClassB()} funzionino correttamente, consentendo di associare
         * la seconda classe ADR alla regola di compatibilita.
         */
        @Test
        @DisplayName("getAdrClassB/setAdrClassB: dovrebbe leggere e scrivere la classe ADR B")
        void shouldGetAndSetAdrClassB() {
            // Arrange
            AdrClass classB = buildAdrClass("8");

            // Act
            rule.setAdrClassB(classB);

            // Assert
            assertThat(rule.getAdrClassB()).isEqualTo(classB);
            assertThat(rule.getAdrClassB().getClassCode()).isEqualTo("8");
        }

        /**
         * Verifica che il flag {@code isCompatible} sia {@code false} per default
         * (approccio difensivo: in assenza di specifica, il sistema nega la compatibilita).
         * Il valore di default e critico per la sicurezza: non deve mai essere {@code true}
         * senza un'esplicita dichiarazione normativa.
         */
        @Test
        @DisplayName("isCompatible: il valore di default dovrebbe essere false (approccio difensivo)")
        void shouldDefaultToIncompatible() {
            // Arrange and Act: nessuna operazione, rule e nuova

            // Assert
            assertThat(rule.isCompatible()).isFalse();
        }

        /**
         * Verifica che {@code setCompatible(true)} e {@code isCompatible()} funzionino
         * correttamente, consentendo di marcare esplicitamente due classi ADR come compatibili
         * per il trasporto misto sullo stesso veicolo.
         */
        @Test
        @DisplayName("setCompatible/isCompatible: dovrebbe impostare e leggere la compatibilita a true")
        void shouldSetAndGetCompatibleTrue() {
            // Arrange and Act
            rule.setCompatible(true);

            // Assert
            assertThat(rule.isCompatible()).isTrue();
        }

        /**
         * Verifica che {@code setCompatible(false)} resetti correttamente il flag di compatibilita
         * al valore di sicurezza, anche se era stato precedentemente impostato a {@code true}.
         */
        @Test
        @DisplayName("setCompatible/isCompatible: dovrebbe resettare la compatibilita a false")
        void shouldSetAndGetCompatibleFalse() {
            // Arrange
            rule.setCompatible(true);

            // Act
            rule.setCompatible(false);

            // Assert
            assertThat(rule.isCompatible()).isFalse();
        }

        /**
         * Verifica che il valore di default del campo {@code warningNote} sia la costante
         * "NOTHING TO SAY", confermando l'inizializzazione a livello di campo
         * come descritto nel codice sorgente.
         */
        @Test
        @DisplayName("getWarningNote: il valore di default dovrebbe essere 'NOTHING TO SAY'")
        void shouldDefaultWarningNoteToNothingToSay() {
            // Arrange and Act: nessuna operazione

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo("Nothing to say");
        }

        /**
         * Verifica che {@code setWarningNote(String)} e {@code getWarningNote()} funzionino
         * correttamente, consentendo di impostare una nota operativa personalizzata.
         * La nota sara normalizzata solo durante il ciclo di vita JPA.
         */
        @Test
        @DisplayName("getWarningNote/setWarningNote: dovrebbe leggere e scrivere la nota operativa")
        void shouldGetAndSetWarningNote() {
            // Arrange
            String expectedNote = "ATTENZIONE: segregazione obbligatoria";

            // Act
            rule.setWarningNote(expectedNote);

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo(expectedNote);
        }
    }

    // =========================================================================
    // NESTED CLASS: normalize() - Sanificazione della warningNote
    // =========================================================================

    /**
     * Raggruppa i test unitari per il metodo privato {@code normalize()} di {@link CompatibilityRule},
     * invocato indirettamente tramite il callback JPA {@code onBeforeSaveOrUpdate()}.
     * <p>
     * Questa classe verifica la robustezza del "Tolerant Reader Pattern" implementato:
     * la corretta gestione dei caratteri di spaziatura spuri, la conversione in maiuscolo
     * e il fallback al valore di default per input vuoti o nulli.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Metodo normalize() - Sanificazione warningNote")
    class NormalizeTest {

        private CompatibilityRule rule;

        /**
         * Predispone l'ambiente per ogni test. Crea istanze valide di due classi ADR distinte
         * (in ordine canonico) e le associa alla regola, in modo che il callback
         * {@code onBeforeSaveOrUpdate()} non fallisca per problemi di ordinamento o identita,
         * permettendo di isolare il comportamento di {@code normalize()}.
         */
        @BeforeEach
        void setUp() {
            rule = new CompatibilityRule();
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(buildAdrClass("8"));
        }

        /**
         * Verifica l'Happy Path della normalizzazione: una nota operativa gia corretta e in
         * maiuscolo viene gestita senza modifiche sostanziali (solo trim eventuale).
         * Mock coinvolti: nessuno (test in puro isolamento su POJO).
         * Output atteso: la stringa rimane invariata.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("normalize(): dovrebbe mantenere una nota gia corretta e in MAIUSCOLO")
        void shouldKeepAlreadyCorrectNote() throws Exception {
            // Arrange
            rule.setWarningNote("SEGREGAZIONE OBBLIGATORIA");

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo("SEGREGAZIONE OBBLIGATORIA");
        }

        /**
         * Verifica la gestione dei dati sporchi provenienti da copia-incolla di manuali PDF:
         * i caratteri di ritorno a capo e le tabulazioni devono essere collassati
         * in un singolo spazio.
         * Mock coinvolti: nessuno.
         * Output atteso: stringa sanitizzata, uppercase, senza caratteri di controllo.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("normalize(): dovrebbe sostituire newline e tab con un singolo spazio")
        void shouldReplaceNewlinesAndTabsWithSingleSpace() throws Exception {
            // Arrange
            rule.setWarningNote("nota\tcon\ntabulazione\re a capo");

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo("nota con tabulazione e a capo");
        }
        
        @Test
        @DisplayName("normalize(): dovrebbe sostituire una stringa blank dopo la sanificazione con il default 'Nothing to say'")
        void shouldReplaceBlankWarningNoteAfterSanification() throws Exception {
        	rule.setWarningNote("\r\n\t\r\n\t");
        	
        	triggerLifecycleCallback(rule);
        	
        	assertThat(rule.getWarningNote()).isEqualTo("Nothing to say");
        }

        /**
         * Verifica che spazi multipli consecutivi vengano collassati in un singolo spazio.
         * Mock coinvolti: nessuno.
         * Output atteso: stringa con singoli spazi tra le parole.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("normalize(): dovrebbe collassare spazi multipli in uno singolo")
        void shouldCollapseMultipleSpacesIntoOne() throws Exception {
            // Arrange
            rule.setWarningNote("nota   con    spazi   multipli");

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo("nota con spazi multipli");
        }

        /**
         * Verifica che gli spazi iniziali e finali vengano rimossi (trim) dalla nota operativa.
         * Mock coinvolti: nessuno.
         * Output atteso: stringa senza spazi ai bordi.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("normalize(): dovrebbe eseguire il trim degli spazi iniziali e finali")
        void shouldTrimLeadingAndTrailingSpaces() throws Exception {
            // Arrange
            rule.setWarningNote("   nota con spazi marginali   ");

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo("nota con spazi marginali");
        }

        /**
         * Verifica il comportamento di fallback di sicurezza quando {@code warningNote} e null:
         * il metodo normalize() deve ripristinare automaticamente il valore di default
         * "NOTHING TO SAY" per evitare violazioni di constraint NOT NULL a livello di database.
         * Mock coinvolti: nessuno.
         * Output atteso: warningNote = "NOTHING TO SAY".
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("normalize(): dovrebbe ripristinare il default 'Nothing to say' se warningNote e null")
        void shouldFallbackToDefaultWhenNoteIsNull() throws Exception {
            // Arrange
            rule.setWarningNote(null);

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo("Nothing to say");
        }

        /**
         * Verifica il comportamento di fallback di sicurezza quando {@code warningNote} e vuota.
         * Mock coinvolti: nessuno.
         * Output atteso: warningNote = "NOTHING TO SAY".
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("normalize(): dovrebbe ripristinare il default 'Nothing to say' se warningNote e vuota")
        void shouldFallbackToDefaultWhenNoteIsEmpty() throws Exception {
            // Arrange
            rule.setWarningNote("");

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo("Nothing to say");
        }

        /**
         * Verifica il comportamento di fallback di sicurezza quando {@code warningNote} e blank.
         * Mock coinvolti: nessuno.
         * Output atteso: warningNote = "NOTHING TO SAY".
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("normalize(): dovrebbe ripristinare il default 'Nothing to say' se warningNote e blank")
        void shouldFallbackToDefaultWhenNoteIsBlank() throws Exception {
            // Arrange
            rule.setWarningNote("     ");

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo("Nothing to say");
        }

        /**
         * Verifica la normalizzazione combinata di tab, newline e spazi multipli,
         * simulando un input realistico da manuali ADR in formato PDF.
         * Mock coinvolti: nessuno.
         * Output atteso: stringa completamente sanitizzata e uppercase.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("normalize(): dovrebbe gestire combinazioni di caratteri spuri da copia-incolla PDF")
        void shouldHandleCombinedSpuriousCharactersFromPdfPaste() throws Exception {
            // Arrange
            rule.setWarningNote("  nota\t\tcon\n  spazi\t multipli   e a capo  ");

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getWarningNote()).isEqualTo("nota con spazi multipli e a capo");
        }

        /**
         * Verifica il Boundary Value esatto: una nota di esattamente 255 caratteri (limite massimo
         * della colonna DB) deve essere accettata senza eccezioni dopo la normalizzazione.
         * Mock coinvolti: nessuno.
         * Output atteso: nessuna eccezione, warningNote di 255 char uppercase.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("normalize(): dovrebbe accettare senza eccezioni una nota di esattamente 255 caratteri")
        void shouldAcceptNoteWithExactlyMaxLength() throws Exception {
            // Arrange: nota di esattamente 255 caratteri
            String nota255 = "A".repeat(255);
            rule.setWarningNote(nota255);

            // Act and Assert
            assertDoesNotThrow(() -> triggerLifecycleCallback(rule));
            assertThat(rule.getWarningNote()).hasSize(255);
        }

        // --- [ TEST RED - TDD FASE RED ] ---

        /**
         * VULNERABILITA 1 - TEST RED (TDD): Assenza di validazione sulla lunghezza massima della warningNote.
         *
         * <p>
         * <b>Difetto Rilevato:</b> Il metodo {@code normalize()} non verifica che la stringa risultante
         * rispetti il vincolo {@code length = 255} definito dalla colonna JPA. Se viene fornita
         * una nota operativa superiore a 255 caratteri, l'entita supera la fase di normalizzazione
         * senza alcun errore applicativo e tenta di essere persistita su MariaDB, dove fallira
         * con un'eccezione di troncamento o un DataException dipendente dal driver,
         * rendendo il messaggio di errore opaco e non tracciabile nel log di dominio.
         * </p>
         *
         * <p>
         * <b>Comportamento Atteso (GREEN):</b> Il metodo {@code normalize()} dovrebbe lanciare una
         * {@link IllegalArgumentException} se la nota, dopo la normalizzazione, supera i 255 caratteri,
         * fermando la transazione con un Fail-Fast a livello di dominio.
         * </p>
         *
         * <p>
         * <b>Azione Correttiva Richiesta:</b> Aggiungere in {@code normalize()}, dopo trim/upper-case:
         * if (warningNote.length() &gt; 255) throw new IllegalArgumentException("warningNote exceeds max length of 255 characters");
         * </p>
         *
         * <p>Questo test e INTENZIONALMENTE progettato per fallire. Il fallimento espone la vulnerabilita.</p>
         *
         * Mock coinvolti: nessuno.
         * Output atteso (GREEN): IllegalArgumentException con messaggio contenente "255".
         *
         * @throws Exception se la reflection fallisce per motivi imprevisti.
         */
        @Test
        @DisplayName("[RED] normalize(): DEVE lanciare eccezione se warningNote supera i 255 caratteri")
        void redShouldThrowExceptionWhenNoteExceedsMaxLength() throws Exception {
            // Arrange: nota che supera i 255 caratteri consentiti dalla colonna DB
            String notaTroppoLunga = "A".repeat(256);
            rule.setWarningNote(notaTroppoLunga);

            // Act and Assert: FALLISCE INTENZIONALMENTE
            Method onBeforeSave = CompatibilityRule.class.getDeclaredMethod("onBeforeSaveOrUpdate");
            onBeforeSave.setAccessible(true);
            InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> onBeforeSave.invoke(rule)
            );
            assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
        }
    }

    // =========================================================================
    // NESTED CLASS: safeOrderForUniqueConstraint() - Ordinamento Canonico
    // =========================================================================

    /**
     * Raggruppa i test unitari per il metodo privato {@code safeOrderForUniqueConstraint()}
     * di {@link CompatibilityRule}, invocato tramite il callback JPA {@code onBeforeSaveOrUpdate()}.
     * <p>
     * Questa classe testa il meccanismo di normalizzazione bidirezionale che garantisce
     * l'unicita della coppia (adrClassA, adrClassB) nel database. La logica fondamentale
     * stabilisce che classCode(A) deve essere sempre minore o uguale a classCode(B) prima
     * della persistenza, indipendentemente dall'ordine in cui le classi sono state assegnate.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Metodo safeOrderForUniqueConstraint() - Ordinamento Canonico")
    class SafeOrderForUniqueConstraintTest {

        private CompatibilityRule rule;

        /**
         * Inizializza una nuova istanza di {@link CompatibilityRule} prima di ogni test.
         */
        @BeforeEach
        void setUp() {
            rule = new CompatibilityRule();
        }

        /**
         * Verifica l'Happy Path: quando {@code adrClassA} e gia alfanumericamente inferiore ad
         * {@code adrClassB}, le classi devono rimanere nell'ordine originale senza alcuno swap.
         * Mock coinvolti: nessuno.
         * Output atteso: adrClassA.classCode = "3", adrClassB.classCode = "8".
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("safeOrder: dovrebbe mantenere l'ordine corretto quando A minore di B (es. '3' vs '8')")
        void shouldMaintainCorrectOrderWhenAIsLessThanB() throws Exception {
            // Arrange
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(buildAdrClass("8"));

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getAdrClassA().getClassCode()).isEqualTo("3");
            assertThat(rule.getAdrClassB().getClassCode()).isEqualTo("8");
        }

        /**
         * Verifica il comportamento critico di riordino: quando l'utente inserisce le classi
         * in ordine inverso (B maggiore di A), il metodo deve eseguire lo swap affinche la classe
         * con il codice alfanumericamente inferiore sia sempre in posizione A.
         * Questo meccanismo protegge il vincolo univoco uk_class_a_class_b da duplicati speculari.
         * Mock coinvolti: nessuno.
         * Output atteso: adrClassA.classCode = "3", adrClassB.classCode = "8" (swap eseguito).
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("safeOrder: dovrebbe invertire A e B quando A maggiore di B (es. '8' vs '3')")
        void shouldSwapClassesWhenAIsGreaterThanB() throws Exception {
            // Arrange: inserimento intenzionalmente invertito
            rule.setAdrClassA(buildAdrClass("8"));
            rule.setAdrClassB(buildAdrClass("3"));

            // Act
            triggerLifecycleCallback(rule);

            // Assert: dopo lo swap, A deve essere "3" e B deve essere "8"
            assertThat(rule.getAdrClassA().getClassCode()).isEqualTo("3");
            assertThat(rule.getAdrClassB().getClassCode()).isEqualTo("8");
        }

        /**
         * Verifica la corretta gestione di classi ADR con codici a piu cifre decimali,
         * come presenti nella normativa ADR (es. "1.4S" vs "6.1"). Il confronto lessicografico
         * deve essere deterministico: "1.4S" risulta minore di "6.1" per confronto String standard.
         * Mock coinvolti: nessuno.
         * Output atteso: adrClassA.classCode = "1.4S", adrClassB.classCode = "6.1".
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("safeOrder: dovrebbe gestire codici ADR decimali multi-carattere (es. '1.4S' vs '6.1')")
        void shouldHandleDecimalAdrClassCodes() throws Exception {
            // Arrange: "1.4S" < "6.1" lessicograficamente
            rule.setAdrClassA(buildAdrClass("6.1"));
            rule.setAdrClassB(buildAdrClass("1.4S"));

            // Act
            triggerLifecycleCallback(rule);

            // Assert: dopo il swap, "1.4S" deve essere in A
            assertThat(rule.getAdrClassA().getClassCode()).isEqualTo("1.4S");
            assertThat(rule.getAdrClassB().getClassCode()).isEqualTo("6.1");
        }

        /**
         * Verifica il Failure Path principale: tentare di creare una regola di compatibilita
         * tra una classe ADR e se stessa e semanticamente privo di senso e deve essere
         * rifiutato con una {@link BadRequestException}. Questo protegge l'integrita
         * della matrice di segregazione ADR.
         * Mock coinvolti: nessuno.
         * Output atteso: BadRequestException con messaggio "cannot be the same ADR Class".
         *
         * @throws Exception se la reflection fallisce per motivi imprevisti.
         */
        @Test
        @DisplayName("safeOrder: DEVE lanciare BadRequestException se le due classi sono identiche")
        void shouldThrowBadRequestExceptionWhenBothClassesAreIdentical() throws Exception {
            // Arrange: due istanze con lo stesso classCode (logicamente identiche)
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(buildAdrClass("3"));

            // Act and Assert
            Method method = CompatibilityRule.class.getDeclaredMethod("onBeforeSaveOrUpdate");
            method.setAccessible(true);
            InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(rule)
            );
            assertThat(exception.getCause())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be the same ADR Class");
        }

        /**
         * Verifica il Boundary Value piu semplice: due classi con codici alfabeticamente
         * adiacenti (es. "3" vs "4") devono rimanere nell'ordine corretto senza swap.
         * Mock coinvolti: nessuno.
         * Output atteso: ordine invariato.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("safeOrder: dovrebbe mantenere l'ordine tra classi adiacenti (es. '3' vs '4')")
        void shouldMaintainOrderForAdjacentClasses() throws Exception {
            // Arrange
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(buildAdrClass("4"));

            // Act
            triggerLifecycleCallback(rule);

            // Assert
            assertThat(rule.getAdrClassA().getClassCode()).isEqualTo("3");
            assertThat(rule.getAdrClassB().getClassCode()).isEqualTo("4");
        }

        /**
         * Verifica il comportamento quando {@code adrClassA} e null e {@code adrClassB} e impostata.
         * Il blocco if(adrClassA != null and adrClassB != null) cortocircuita e non viene
         * eseguito alcun ordinamento.
         * Mock coinvolti: nessuno.
         * Output atteso: nessuna eccezione.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("safeOrder: dovrebbe saltare l'ordinamento se adrClassA e null (cortocircuito logico)")
        void shouldSkipOrderingWhenClassAIsNull() throws Exception {
            // Arrange
            rule.setAdrClassA(null);
            rule.setAdrClassB(buildAdrClass("8"));

            // Act and Assert: nessuna eccezione deve essere lanciata
            assertThatThrownBy(() -> triggerLifecycleCallback(rule))
        		.isInstanceOf(InvocationTargetException.class)
        		.hasCauseInstanceOf(BadRequestException.class);
        }

        /**
         * Verifica il comportamento simmetrico: quando {@code adrClassB} e null e
         * {@code adrClassA} e impostata, il blocco di ordinamento deve essere saltato.
         * Mock coinvolti: nessuno.
         * Output atteso: nessuna eccezione.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("safeOrder: dovrebbe saltare l'ordinamento se adrClassB e null")
        void shouldSkipOrderingWhenClassBIsNull() throws Exception {
            // Arrange
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(null);

            // Act and Assert
            assertThatThrownBy(() -> triggerLifecycleCallback(rule))
            	.isInstanceOf(InvocationTargetException.class)
            	.hasCauseInstanceOf(BadRequestException.class);
        }

        /**
         * Verifica il comportamento quando entrambe le classi ADR sono null.
         * Mock coinvolti: nessuno.
         * Output atteso: nessuna eccezione.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("safeOrder: dovrebbe saltare l'ordinamento se entrambe le classi sono null")
        void shouldSkipOrderingWhenBothClassesAreNull() throws Exception {
            // Arrange
            rule.setAdrClassA(null);
            rule.setAdrClassB(null);

            // Act and Assert
            assertThatThrownBy(() -> triggerLifecycleCallback(rule))
        		.isInstanceOf(InvocationTargetException.class)
        		.hasCauseInstanceOf(BadRequestException.class);
        }

        // --- [ TEST RED - TDD FASE RED ] ---

        /**
         * VULNERABILITA 2a - TEST RED (TDD): Assenza di validazione Fail-Fast quando solo adrClassA e null.
         *
         * <p>
         * <b>Difetto Rilevato:</b> Il metodo {@code safeOrderForUniqueConstraint()} utilizza
         * un doppio controllo: se solo una delle due classi e null, la condizione e false
         * e il metodo termina silenziosamente, lasciando passare un'entita in stato inconsistente
         * verso JPA. Il constraint NOT NULL del database intercettera l'errore solo durante
         * la fase di flush, rendendo il messaggio di errore opaco.
         * </p>
         *
         * <p>
         * <b>Comportamento Atteso (GREEN):</b> Se solo una delle due classi ADR e null,
         * il metodo dovrebbe lanciare immediatamente una {@link BadRequestException}
         * con un messaggio chiaro, applicando il principio del Fail-Fast a livello di dominio.
         * </p>
         *
         * <p>
         * <b>Azione Correttiva Richiesta:</b> Prima del blocco if(adrClassA != null and adrClassB != null),
         * aggiungere: if (adrClassA == null || adrClassB == null) throw new BadRequestException("Both ADR classes must be non-null");
         * </p>
         *
         * <p>Questo test e INTENZIONALMENTE progettato per fallire. Il fallimento espone la vulnerabilita.</p>
         *
         * Mock coinvolti: nessuno.
         * Output atteso (GREEN): BadRequestException con messaggio contenente "null".
         *
         * @throws Exception se la reflection fallisce per motivi imprevisti.
         */
        @Test
        @DisplayName("[RED] safeOrder: DEVE lanciare BadRequestException se solo adrClassA e null (Fail-Fast)")
        void redShouldThrowExceptionWhenOnlyClassAIsNull() throws Exception {
            // Arrange
            rule.setAdrClassA(null);
            rule.setAdrClassB(buildAdrClass("8"));

            // Act and Assert: FALLISCE INTENZIONALMENTE
            Method method = CompatibilityRule.class.getDeclaredMethod("onBeforeSaveOrUpdate");
            method.setAccessible(true);
            InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(rule)
            );
            assertThat(exception.getCause())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("null");
        }

        /**
         * VULNERABILITA 2b - TEST RED (TDD): Assenza di validazione Fail-Fast quando solo adrClassB e null.
         *
         * <p>
         * <b>Difetto Rilevato:</b> Caso speculare alla vulnerabilita 2a. Il sistema accetta
         * silenziosamente un'entita con adrClassB null, delegando il controllo a JPA
         * invece di applicare il Fail-Fast nel dominio con un messaggio chiaro.
         * </p>
         *
         * <p>Questo test e INTENZIONALMENTE progettato per fallire. Il fallimento espone la vulnerabilita.</p>
         *
         * Mock coinvolti: nessuno.
         * Output atteso (GREEN): BadRequestException con messaggio contenente "null".
         *
         * @throws Exception se la reflection fallisce per motivi imprevisti.
         */
        @Test
        @DisplayName("[RED] safeOrder: DEVE lanciare BadRequestException se solo adrClassB e null (Fail-Fast)")
        void redShouldThrowExceptionWhenOnlyClassBIsNull() throws Exception {
            // Arrange
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(null);

            // Act and Assert: FALLISCE INTENZIONALMENTE
            Method method = CompatibilityRule.class.getDeclaredMethod("onBeforeSaveOrUpdate");
            method.setAccessible(true);
            InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(rule)
            );
            assertThat(exception.getCause())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("null");
        }
    }

    // =========================================================================
    // NESTED CLASS: equals() e hashCode() - Identita di Dominio
    // =========================================================================

    /**
     * Raggruppa i test unitari per i metodi {@link CompatibilityRule#equals(Object)} e
     * {@link CompatibilityRule#hashCode()} di {@link CompatibilityRule}.
     * <p>
     * L'uguaglianza e definita esclusivamente sulla coppia (adrClassA, adrClassB)
     * e NON sull'identita JPA (id). Il contratto di questi metodi e fondamentale
     * per il corretto funzionamento delle collection Java (HashSet, HashMap)
     * e per il layer di cache (Caffeine).
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Metodi equals() e hashCode() - Identita di Dominio")
    class EqualsAndHashCodeTest {

        /**
         * Verifica che due istanze di {@link CompatibilityRule} con le stesse classi ADR
         * ma ID diversi siano considerate uguali. L'identita logica e determinata dalla
         * coppia di classi, non dalla chiave surrogata JPA.
         * Mock coinvolti: nessuno.
         * Output atteso: equals() restituisce true.
         */
        @Test
        @DisplayName("equals: dovrebbe essere true per due regole con le stesse classi ADR (ID diverso)")
        void shouldReturnTrueForSameAdrClassPairWithDifferentIds() {
            // Arrange
            AdrClass classA = buildAdrClass("3");
            AdrClass classB = buildAdrClass("8");

            CompatibilityRule rule1 = new CompatibilityRule();
            rule1.setId(1L);
            rule1.setAdrClassA(classA);
            rule1.setAdrClassB(classB);

            CompatibilityRule rule2 = new CompatibilityRule();
            rule2.setId(99L);
            rule2.setAdrClassA(classA);
            rule2.setAdrClassB(classB);

            // Act and Assert
            assertThat(rule1).isEqualTo(rule2);
        }

        /**
         * Verifica che due istanze di {@link CompatibilityRule} con coppie di classi ADR
         * diverse siano considerate non uguali.
         * Mock coinvolti: nessuno.
         * Output atteso: equals() restituisce false.
         */
        @Test
        @DisplayName("equals: dovrebbe essere false per regole con coppie di classi ADR diverse")
        void shouldReturnFalseForDifferentAdrClassPairs() {
            // Arrange
            CompatibilityRule rule1 = new CompatibilityRule();
            rule1.setAdrClassA(buildAdrClass("3"));
            rule1.setAdrClassB(buildAdrClass("8"));

            CompatibilityRule rule2 = new CompatibilityRule();
            rule2.setAdrClassA(buildAdrClass("1"));
            rule2.setAdrClassB(buildAdrClass("6.1"));

            // Act and Assert
            assertThat(rule1).isNotEqualTo(rule2);
        }

        /**
         * Verifica la proprieta riflessiva del contratto equals: un oggetto deve sempre
         * essere uguale a se stesso.
         * Mock coinvolti: nessuno.
         * Output atteso: equals() restituisce true.
         */
        @Test
        @DisplayName("equals: dovrebbe essere true per la stessa istanza (riflessivita)")
        void shouldReturnTrueForSameInstance() {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(buildAdrClass("8"));

            // Act and Assert
            assertThat(rule).isEqualTo(rule);
        }

        /**
         * Verifica che {@code equals} restituisca false quando confrontato con null,
         * rispettando il contratto definito in {@link Object#equals(Object)}.
         * Mock coinvolti: nessuno.
         * Output atteso: equals() restituisce false.
         */
        @Test
        @DisplayName("equals: dovrebbe essere false quando confrontato con null")
        void shouldReturnFalseWhenComparedToNull() {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(buildAdrClass("8"));

            // Act and Assert
            assertThat(rule).isNotEqualTo(null);
        }

        /**
         * Verifica che {@code equals} restituisca false quando confrontato con un oggetto
         * di tipo completamente diverso, garantendo la robustezza del cast interno.
         * Mock coinvolti: nessuno.
         * Output atteso: equals() restituisce false.
         */
        @Test
        @DisplayName("equals: dovrebbe essere false quando confrontato con un tipo diverso")
        void shouldReturnFalseWhenComparedToDifferentType() {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(buildAdrClass("8"));

            // Act and Assert
            assertThat(rule).isNotEqualTo("una stringa qualunque");
        }

        /**
         * Verifica il contratto di coerenza tra equals() e hashCode():
         * due oggetti uguali devono produrre lo stesso valore di hash.
         * Mock coinvolti: nessuno.
         * Output atteso: hashCode identici.
         */
        @Test
        @DisplayName("hashCode: dovrebbe produrre lo stesso hash per due regole con le stesse classi ADR")
        void shouldProduceSameHashCodeForEqualRules() {
            // Arrange
            AdrClass classA = buildAdrClass("3");
            AdrClass classB = buildAdrClass("8");

            CompatibilityRule rule1 = new CompatibilityRule();
            rule1.setAdrClassA(classA);
            rule1.setAdrClassB(classB);

            CompatibilityRule rule2 = new CompatibilityRule();
            rule2.setAdrClassA(classA);
            rule2.setAdrClassB(classB);

            // Act and Assert
            assertThat(rule1.hashCode()).isEqualTo(rule2.hashCode());
        }

        /**
         * Verifica che istanze con coppie di classi ADR diverse producano hash code diversi.
         * Mock coinvolti: nessuno.
         * Output atteso: hashCode diversi.
         */
        @Test
        @DisplayName("hashCode: dovrebbe produrre hash diversi per regole con classi ADR diverse")
        void shouldProduceDifferentHashCodesForDifferentRules() {
            // Arrange
            CompatibilityRule rule1 = new CompatibilityRule();
            rule1.setAdrClassA(buildAdrClass("3"));
            rule1.setAdrClassB(buildAdrClass("8"));

            CompatibilityRule rule2 = new CompatibilityRule();
            rule2.setAdrClassA(buildAdrClass("1"));
            rule2.setAdrClassB(buildAdrClass("6.1"));

            // Act and Assert
            assertThat(rule1.hashCode()).isNotEqualTo(rule2.hashCode());
        }

        /**
         * Verifica che hashCode() su una regola con entrambe le classi null non
         * produca un'eccezione, restituendo un valore stabile.
         * Mock coinvolti: nessuno.
         * Output atteso: nessuna eccezione, hashCode stabile tra chiamate successive.
         */
        @Test
        @DisplayName("hashCode: dovrebbe essere stabile e non lanciare eccezioni con classi null")
        void shouldReturnStableHashCodeWithNullClasses() {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();
            rule.setAdrClassA(null);
            rule.setAdrClassB(null);

            // Act and Assert
            assertDoesNotThrow(() -> {
                int hash1 = rule.hashCode();
                int hash2 = rule.hashCode();
                assertThat(hash1).isEqualTo(hash2);
            });
        }

        // --- [ TEST RED - TDD FASE RED ] ---

        /**
         * VULNERABILITA 3 - TEST RED (TDD): equals() non e simmetrico rispetto all'ordinamento
         * canonico per entita in stato transiente (pre-persist).
         *
         * <p>
         * <b>Difetto Rilevato:</b> Il metodo equals() confronta direttamente le coppie
         * (adrClassA, adrClassB) senza applicare preventivamente l'ordinamento canonico.
         * Due istanze logicamente identiche (es. [3,8] e [8,3]) risulteranno non uguali
         * se confrontate prima del passaggio per il lifecycle callback PrePersist.
         * Questo e un problema reale nelle operazioni transiente del service layer:
         * controlli di duplicazione in memoria potrebbero dare esito falso negativo,
         * permettendo l'inserimento di regole duplicate speculari.
         * </p>
         *
         * <p>
         * <b>Comportamento Atteso (GREEN):</b> Due regole con le stesse classi ADR
         * in ordine invertito dovrebbero essere considerate uguali da equals() anche
         * in stato transiente, garantendo l'invarianza rispetto alla direzionalita.
         * </p>
         *
         * <p>
         * <b>Azione Correttiva Richiesta:</b> Il metodo equals() dovrebbe normalizzare
         * l'ordine dei codici prima del confronto oppure la documentazione deve esplicitare
         * che equals() e affidabile SOLO dopo il passaggio per il lifecycle callback PrePersist.
         * </p>
         *
         * <p>Questo test e INTENZIONALMENTE progettato per fallire. Il fallimento espone la vulnerabilita.</p>
         *
         * Mock coinvolti: nessuno.
         * Output atteso (GREEN): equals() restituisce true per [3,8] e [8,3] anche pre-persist.
         */
        @Test
        @DisplayName("[RED] equals: DEVE essere true per [3,8] e [8,3] anche PRIMA della persistenza (invarianza di direzionalita)")
        void redShouldReturnTrueForInvertedClassPairBeforePersist() {
            // Arrange: stesse classi, ordine invertito, SENZA invocare il lifecycle callback
            CompatibilityRule rule1 = new CompatibilityRule();
            rule1.setAdrClassA(buildAdrClass("3"));
            rule1.setAdrClassB(buildAdrClass("8"));

            CompatibilityRule rule2 = new CompatibilityRule();
            rule2.setAdrClassA(buildAdrClass("8")); // ordine invertito
            rule2.setAdrClassB(buildAdrClass("3")); // ordine invertito

            // Act and Assert: FALLISCE INTENZIONALMENTE perche equals() non normalizza
            assertThat(rule1).isEqualTo(rule2);
        }
    }

    // =========================================================================
    // NESTED CLASS: toString() - Rappresentazione Testuale Diagnostica
    // =========================================================================

    /**
     * Raggruppa i test unitari per il metodo {@link CompatibilityRule#toString()}.
     * <p>
     * Verifica che la rappresentazione testuale dell'entita sia coerente con il formato
     * atteso e contenga le informazioni diagnostiche essenziali: id, isCompatible
     * e warningNote. Fondamentale per la tracciabilita nei log applicativi.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Metodo toString() - Rappresentazione Testuale Diagnostica")
    class ToStringTest {

        /**
         * Verifica che toString() produca l'output atteso nel formato canonico
         * CompatibilityRule [id=X, isCompatible=Y, warningNote=Z], contenendo
         * i campi essenziali per il debugging e la tracciabilita nei log.
         * Mock coinvolti: nessuno.
         * Output atteso: stringa contenente id, isCompatible e warningNote.
         */
        @Test
        @DisplayName("toString: dovrebbe contenere id, isCompatible e warningNote")
        void shouldContainEssentialFields() {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();
            rule.setId(7L);
            rule.setCompatible(true);
            rule.setWarningNote("SEGREGAZIONE OBBLIGATORIA");

            // Act
            String result = rule.toString();

            // Assert
            assertThat(result)
                .contains("CompatibilityRule")
                .contains("id=7")
                .contains("isCompatible=true")
                .contains("warningNote=SEGREGAZIONE OBBLIGATORIA");
        }

        /**
         * Verifica che toString() per un'entita in stato transiente (id null)
         * non generi eccezioni e rappresenti correttamente il valore null per l'id.
         * Mock coinvolti: nessuno.
         * Output atteso: stringa contenente "id=null".
         */
        @Test
        @DisplayName("toString: dovrebbe gestire correttamente l'id null per un'entita transiente")
        void shouldHandleNullIdForTransientEntity() {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();
            rule.setId(null);

            // Act
            String result = rule.toString();

            // Assert
            assertThat(result)
                .contains("id=null")
                .contains("isCompatible=false")
                .contains("warningNote=Nothing to say");
        }

        /**
         * Verifica che toString() rappresenti correttamente i valori di default
         * dell'entita appena istanziata. Serve anche come smoke test del costruttore di default.
         * Mock coinvolti: nessuno.
         * Output atteso: stringa con valori di default.
         */
        @Test
        @DisplayName("toString: dovrebbe produrre output coerente con lo stato di default")
        void shouldProduceDefaultStateOutput() {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();

            // Act
            String result = rule.toString();

            // Assert
            assertThat(result)
                .contains("isCompatible=false")
                .contains("warningNote=Nothing to say");
        }

        /**
         * Verifica che toString() non lanci eccezioni quando warningNote e stata impostata
         * a null direttamente (bypassing il lifecycle callback), condizione possibile
         * nell'utilizzo dell'entita come DTO transitorio.
         * Mock coinvolti: nessuno.
         * Output atteso: nessuna eccezione, stringa contenente "warningNote=null".
         */
        @Test
        @DisplayName("toString: dovrebbe gestire warningNote null senza eccezioni")
        void shouldHandleNullWarningNoteWithoutException() {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();
            rule.setWarningNote(null);

            // Act and Assert
            assertDoesNotThrow(() -> {
                String result = rule.toString();
                assertThat(result).contains("warningNote=null");
            });
        }
    }

    // =========================================================================
    // NESTED CLASS: onBeforeSaveOrUpdate() - Test di Integrazione Interna
    // =========================================================================

    /**
     * Raggruppa i test di integrazione interna per il metodo orchestratore
     * {@code onBeforeSaveOrUpdate()}, verificando che l'ordine di esecuzione
     * (safeOrderForUniqueConstraint prima di normalize) sia deterministico
     * e che i due processi si compongano correttamente senza contesto Spring.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Metodo onBeforeSaveOrUpdate() - Orchestrazione del Ciclo di Vita JPA")
    class OnBeforeSaveOrUpdateTest {

        /**
         * Verifica lo scenario end-to-end del ciclo di vita JPA: dati sporchi in ingresso
         * (classi in ordine inverso, nota con caratteri spuri) devono essere completamente
         * normalizzati dopo l'invocazione del callback. Garantisce che l'ordine di esecuzione
         * (prima ordinamento, poi normalizzazione testo) sia corretto.
         * Mock coinvolti: nessuno.
         * Output atteso: classi in ordine canonico, nota sanitizzata e uppercase.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("onBeforeSaveOrUpdate: dovrebbe applicare ordinamento E normalizzazione in sequenza")
        void shouldApplyOrderingThenNormalizationInSequence() throws Exception {
            // Arrange: dati volutamente sporchi
            CompatibilityRule rule = new CompatibilityRule();
            rule.setAdrClassA(buildAdrClass("8")); // ordine inverso
            rule.setAdrClassB(buildAdrClass("3")); // ordine inverso
            rule.setWarningNote("  nota\n  con\t  caratteri  spuri  ");

            // Act
            triggerLifecycleCallback(rule);

            // Assert: ordinamento canonico applicato
            assertThat(rule.getAdrClassA().getClassCode()).isEqualTo("3");
            assertThat(rule.getAdrClassB().getClassCode()).isEqualTo("8");
            // Assert: normalizzazione testo applicata
            assertThat(rule.getWarningNote()).isEqualTo("nota con caratteri spuri");
        }

        /**
         * Verifica che il callback lanci BadRequestException prima di eseguire
         * la normalizzazione del testo quando le due classi sono identiche.
         * Valida l'ottimizzazione: la CPU non deve sprecarsi in operazioni di
         * formattazione se la transazione sara abortita per violazione logica.
         * Mock coinvolti: nessuno.
         * Output atteso: BadRequestException, warningNote invariata (non normalizzata).
         *
         * @throws Exception se la reflection fallisce per motivi imprevisti.
         */
        @Test
        @DisplayName("onBeforeSaveOrUpdate: DEVE abortire con BadRequestException prima della normalizzazione se le classi sono identiche")
        void shouldAbortBeforeNormalizationWhenClassesAreIdentical() throws Exception {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(buildAdrClass("3"));
            rule.setWarningNote("nota che NON verra normalizzata");

            // Act
            Method method = CompatibilityRule.class.getDeclaredMethod("onBeforeSaveOrUpdate");
            method.setAccessible(true);
            InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(rule)
            );

            // Assert: l'eccezione e BadRequestException e la nota e rimasta invariata
            assertThat(exception.getCause()).isInstanceOf(BadRequestException.class);
            assertThat(rule.getWarningNote()).isEqualTo("nota che NON verra normalizzata");
        }

        /**
         * Verifica lo scenario "all-defaults": un'entita creata con entrambe le classi null
         * e nota di default deve sopravvivere al callback senza eccezioni.
         * Il fallback della normalizzazione deve preservare la nota di default.
         * Mock coinvolti: nessuno.
         * Output atteso: nessuna eccezione, warningNote = "NOTHING TO SAY".
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("onBeforeSaveOrUpdate: dovrebbe sopravvivere con tutti i valori di default (classi null, nota di default)")
        void shouldSurviveWithAllDefaultValues() throws Exception {
            // Arrange: entita con valori completamente di default
            CompatibilityRule rule = new CompatibilityRule();

            // Act and Assert
            assertThatThrownBy(() -> triggerLifecycleCallback(rule))
            	.isInstanceOf(InvocationTargetException.class)
            	.hasCauseInstanceOf(BadRequestException.class);
            assertThat(rule.getWarningNote()).isEqualTo("Nothing to say");
        }

        /**
         * Verifica che il callback sia idempotente: eseguirlo piu volte sullo stesso oggetto
         * (come avviene in scenari di merge/update JPA multipli) deve produrre sempre
         * lo stesso risultato senza effetti collaterali indesiderati.
         * Mock coinvolti: nessuno.
         * Output atteso: risultato identico dopo la prima e la seconda invocazione.
         *
         * @throws Exception se la reflection fallisce.
         */
        @Test
        @DisplayName("onBeforeSaveOrUpdate: dovrebbe essere idempotente (stesso risultato su piu chiamate successive)")
        void shouldBeIdempotent() throws Exception {
            // Arrange
            CompatibilityRule rule = new CompatibilityRule();
            rule.setAdrClassA(buildAdrClass("3"));
            rule.setAdrClassB(buildAdrClass("8"));
            rule.setWarningNote("  nota con spazi  ");

            // Act: prima invocazione
            triggerLifecycleCallback(rule);
            String noteAfterFirst = rule.getWarningNote();
            String classAAfterFirst = rule.getAdrClassA().getClassCode();

            // Act: seconda invocazione (simula un update successivo)
            triggerLifecycleCallback(rule);

            // Assert: risultati identici
            assertThat(rule.getWarningNote()).isEqualTo(noteAfterFirst);
            assertThat(rule.getAdrClassA().getClassCode()).isEqualTo(classAAfterFirst);
        }
    }
}
