package dev.vinciguerra.adrsentinel.db.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Suite di test unitari per la classe JPA Entity {@link Customer}.
 * <p>
 * Questa suite verifica in modo esaustivo tutti i metodi pubblici e il lifecycle hook
 * privato {@code normalize()} dell'entita' Customer, applicando un rigoroso approccio
 * TDD difensivo. I test sono organizzati in classi nested per raggruppamento semantico:
 * </p>
 * <ul>
 *   <li><b>GettersSetters:</b> Verifica di tutti i metodi getter/setter per ciascun campo.</li>
 *   <li><b>CustomerRoleEnum:</b> Verifica dei valori dell'enumerazione innestata {@link Customer.CustomerRole}.</li>
 *   <li><b>NormalizeLifecycleHook:</b> Test del metodo privato {@code normalize()} invocato via
 *       Java Reflection, coprendo Happy Path, Edge Case e Failure Path (incluse fasi RED TDD
 *       per vulnerabilita' individuate).</li>
 *   <li><b>EqualsContract:</b> Verifica del contratto {@code equals()} basato sulla Business Key
 *       {@code vatNumber}, includendo riflessivita', simmetria, transitivita', null-safety e
 *       type-mismatch.</li>
 *   <li><b>HashCodeContract:</b> Verifica del contratto {@code hashCode()} e della coerenza
 *       con {@code equals()}, includendo stabilita', distribuzione e null-safety.</li>
 *   <li><b>ToStringContract:</b> Verifica del formato e del contenuto dell'output di {@code toString()}.</li>
 *   <li><b>CollectionIntegration:</b> Verifica del comportamento di {@code Customer} in
 *       {@code HashSet} e {@code HashMap} per garantire la corretta integrazione con le
 *       Collection di Hibernate.</li>
 *   <li><b>RedTddVulnerabilities:</b> Test in FASE RED deliberata per esporre vulnerabilita'
 *       nel codice sorgente: assenza di null-guard in {@code normalize()}, assenza di validazione
 *       sulla lunghezza dei campi e assenza di validazione del formato della Partita IVA.</li>
 * </ul>
 * <p>
 * <b>Isolamento:</b> Puro — nessun contesto Spring, nessun H2, nessun ORM avviato.
 * L'accesso al metodo privato {@code normalize()} avviene esclusivamente tramite Java Reflection.
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
class CustomerTests {

	private Customer customer;
	private Method normalizeMethod;

	/**
	 * Inizializza un'istanza di {@link Customer} con dati validi e rende accessibile
	 * il metodo privato {@code normalize()} tramite Java Reflection per ogni test.
	 */
	@BeforeEach
	void setUp() throws NoSuchMethodException {
		customer = new Customer();
		customer.setId(1L);
		customer.setCompanyName("Acme Logistics Srl");
		customer.setVatNumber("IT12345678901");
		customer.setLegalAddress("Via Roma 10, 00100 Roma RM");
		customer.setActive(true);

		normalizeMethod = Customer.class.getDeclaredMethod("normalize");
		normalizeMethod.setAccessible(true);
	}

	/**
	 * Invoca il metodo privato {@code normalize()} sull'istanza {@link Customer}
	 * corrente tramite Java Reflection. Propaga eventuali eccezioni applicative
	 * unwrappando l'{@link InvocationTargetException}.
	 *
	 * @throws Exception se il metodo {@code normalize()} lancia un'eccezione
	 */
	private void invokeNormalize() throws Exception {
		try {
			normalizeMethod.invoke(customer);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof Exception ex) {
				throw ex;
			}
			throw e;
		}
	}

	// ========================================================================================
	// GETTERS / SETTERS
	// ========================================================================================

	/**
	 * Test dei metodi getter e setter per tutti i campi dell'entita' {@link Customer}.
	 * Verifica che i valori impostati tramite setter siano restituiti correttamente dai getter,
	 * inclusi i valori null, i valori limite e i valori di default.
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("Getters e Setters")
	class GettersSetters {

		/**
		 * Verifica che {@code getId()} restituisca il valore impostato tramite {@code setId()}.
		 * Il campo {@code id} e' la chiave primaria surrogata generata dal database.
		 */
		@Test
		@DisplayName("getId() deve restituire il valore impostato con setId()")
		void shouldReturnIdSetBySetter() {
			// Arrange
			Long expectedId = 42L;

			// Act
			customer.setId(expectedId);

			// Assert
			assertThat(customer.getId()).isEqualTo(expectedId);
		}

		/**
		 * Verifica che {@code getId()} restituisca {@code null} per un'entita' in stato
		 * transiente (mai persistita sul DB), poiche' la strategia {@code GenerationType.IDENTITY}
		 * assegna l'ID solo al momento del flush.
		 */
		@Test
		@DisplayName("getId() deve restituire null per entita' transiente")
		void shouldReturnNullIdForTransientEntity() {
			// Arrange
			Customer transientCustomer = new Customer();

			// Act & Assert
			assertThat(transientCustomer.getId()).isNull();
		}

		/**
		 * Verifica che {@code setId(null)} sia accettato senza eccezioni.
		 * Questo scenario puo' verificarsi in contesti di detach/merge JPA.
		 */
		@Test
		@DisplayName("setId(null) deve essere accettato senza eccezioni")
		void shouldAcceptNullId() {
			// Arrange & Act
			customer.setId(null);

			// Assert
			assertThat(customer.getId()).isNull();
		}

		/**
		 * Verifica che {@code getCompanyName()} restituisca il valore impostato
		 * tramite {@code setCompanyName()}, inclusa la preservazione esatta della stringa
		 * (la normalizzazione avviene solo nel lifecycle hook JPA, non nel setter).
		 */
		@Test
		@DisplayName("getCompanyName() deve restituire il valore impostato con setCompanyName()")
		void shouldReturnCompanyNameSetBySetter() {
			// Arrange
			String expected = "Transport Italia S.p.A.";

			// Act
			customer.setCompanyName(expected);

			// Assert
			assertThat(customer.getCompanyName()).isEqualTo(expected);
		}

		/**
		 * Verifica che {@code setCompanyName(null)} sia accettato dal setter senza eccezioni.
		 * Il setter non applica validazioni; il vincolo {@code nullable = false} e' delegato al DB.
		 */
		@Test
		@DisplayName("setCompanyName(null) deve essere accettato dal setter")
		void shouldAcceptNullCompanyName() {
			// Arrange & Act
			customer.setCompanyName(null);

			// Assert
			assertThat(customer.getCompanyName()).isNull();
		}

		/**
		 * Verifica che {@code getVatNumber()} restituisca il valore impostato
		 * tramite {@code setVatNumber()}.
		 */
		@Test
		@DisplayName("getVatNumber() deve restituire il valore impostato con setVatNumber()")
		void shouldReturnVatNumberSetBySetter() {
			// Arrange
			String expected = "DE987654321";

			// Act
			customer.setVatNumber(expected);

			// Assert
			assertThat(customer.getVatNumber()).isEqualTo(expected);
		}

		/**
		 * Verifica che {@code setVatNumber(null)} sia accettato dal setter senza eccezioni.
		 * Nessuna validazione e' presente nel setter; il vincolo e' delegato al DB e al
		 * lifecycle hook {@code normalize()}.
		 */
		@Test
		@DisplayName("setVatNumber(null) deve essere accettato dal setter")
		void shouldAcceptNullVatNumber() {
			// Arrange & Act
			customer.setVatNumber(null);

			// Assert
			assertThat(customer.getVatNumber()).isNull();
		}

		/**
		 * Verifica che {@code getLegalAddress()} restituisca il valore impostato
		 * tramite {@code setLegalAddress()}.
		 */
		@Test
		@DisplayName("getLegalAddress() deve restituire il valore impostato con setLegalAddress()")
		void shouldReturnLegalAddressSetBySetter() {
			// Arrange
			String expected = "Piazza del Duomo 1, 20121 Milano MI";

			// Act
			customer.setLegalAddress(expected);

			// Assert
			assertThat(customer.getLegalAddress()).isEqualTo(expected);
		}

		/**
		 * Verifica che {@code setLegalAddress(null)} sia accettato dal setter.
		 */
		@Test
		@DisplayName("setLegalAddress(null) deve essere accettato dal setter")
		void shouldAcceptNullLegalAddress() {
			// Arrange & Act
			customer.setLegalAddress(null);

			// Assert
			assertThat(customer.getLegalAddress()).isNull();
		}

		/**
		 * Verifica che {@code isActive()} restituisca {@code true} dopo
		 * {@code setActive(true)}.
		 */
		@Test
		@DisplayName("isActive() deve restituire true dopo setActive(true)")
		void shouldReturnTrueWhenActiveSetToTrue() {
			// Arrange & Act
			customer.setActive(true);

			// Assert
			assertThat(customer.isActive()).isTrue();
		}

		/**
		 * Verifica che {@code isActive()} restituisca {@code false} dopo
		 * {@code setActive(false)}. Questo simula lo stato di soft-delete.
		 */
		@Test
		@DisplayName("isActive() deve restituire false dopo setActive(false)")
		void shouldReturnFalseWhenActiveSetToFalse() {
			// Arrange & Act
			customer.setActive(false);

			// Assert
			assertThat(customer.isActive()).isFalse();
		}

		/**
		 * Verifica che il valore di default di {@code active} per un'istanza
		 * appena creata sia {@code false} (valore primitivo boolean di default in Java).
		 * Il default {@code @ColumnDefault("true")} e' applicato solo dal DB, non dalla JVM.
		 */
		@Test
		@DisplayName("active deve avere valore di default false (primitivo boolean)")
		void shouldHaveDefaultActiveFalse() {
			// Arrange
			Customer freshCustomer = new Customer();

			// Act & Assert
			assertThat(freshCustomer.isActive()).isFalse();
		}
	}

	// ========================================================================================
	// ENUM CustomerRole
	// ========================================================================================

	/**
	 * Test dell'enumerazione innestata {@link Customer.CustomerRole} che definisce
	 * i ruoli operativi e legali (Mittente, Destinatario, Vettore) assunti da un'azienda
	 * all'interno di un Documento di Trasporto ADR.
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("Enum CustomerRole")
	class CustomerRoleEnum {

		/**
		 * Verifica che l'enumerazione {@code CustomerRole} contenga esattamente tre valori:
		 * SENDER, RECEIVER e CARRIER, nell'ordine di dichiarazione.
		 */
		@Test
		@DisplayName("CustomerRole deve contenere esattamente SENDER, RECEIVER, CARRIER")
		void shouldContainAllThreeRoles() {
			// Arrange & Act
			Customer.CustomerRole[] roles = Customer.CustomerRole.values();

			// Assert
			assertThat(roles)
				.hasSize(3)
				.containsExactly(
					Customer.CustomerRole.SENDER,
					Customer.CustomerRole.RECEIVER,
					Customer.CustomerRole.CARRIER
				);
		}

		/**
		 * Verifica che {@code valueOf("SENDER")} restituisca il valore enum corretto,
		 * confermando la compatibilita' con la deserializzazione da stringa (es. da JSON o DB).
		 */
		@Test
		@DisplayName("valueOf('SENDER') deve restituire CustomerRole.SENDER")
		void shouldResolveValueOfSender() {
			// Arrange & Act
			Customer.CustomerRole role = Customer.CustomerRole.valueOf("SENDER");

			// Assert
			assertThat(role).isEqualTo(Customer.CustomerRole.SENDER);
		}

		/**
		 * Verifica che {@code valueOf("RECEIVER")} restituisca il valore enum corretto.
		 */
		@Test
		@DisplayName("valueOf('RECEIVER') deve restituire CustomerRole.RECEIVER")
		void shouldResolveValueOfReceiver() {
			// Arrange & Act
			Customer.CustomerRole role = Customer.CustomerRole.valueOf("RECEIVER");

			// Assert
			assertThat(role).isEqualTo(Customer.CustomerRole.RECEIVER);
		}

		/**
		 * Verifica che {@code valueOf("CARRIER")} restituisca il valore enum corretto.
		 */
		@Test
		@DisplayName("valueOf('CARRIER') deve restituire CustomerRole.CARRIER")
		void shouldResolveValueOfCarrier() {
			// Arrange & Act
			Customer.CustomerRole role = Customer.CustomerRole.valueOf("CARRIER");

			// Assert
			assertThat(role).isEqualTo(Customer.CustomerRole.CARRIER);
		}

		/**
		 * Verifica che {@code valueOf()} lanci {@link IllegalArgumentException} per un valore
		 * inesistente. Questo test protegge da input non validati provenienti da payload JSON
		 * o parametri di query non controllati dal validatore upstream.
		 */
		@Test
		@DisplayName("valueOf() deve lanciare IllegalArgumentException per valore inesistente")
		void shouldThrowIllegalArgumentExceptionForInvalidValue() {
			// Arrange & Act & Assert
			assertThatThrownBy(() -> Customer.CustomerRole.valueOf("DRIVER"))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// ========================================================================================
	// NORMALIZE (LIFECYCLE HOOK - REFLECTION)
	// ========================================================================================

	/**
	 * Test del metodo privato {@code normalize()}, annotato con {@code @PrePersist} e
	 * {@code @PreUpdate}, che agisce come gatekeeper per l'igiene dei dati prima del
	 * flush JPA. Il metodo applica tre trasformazioni:
	 * <ul>
	 *   <li><b>companyName:</b> trim, collasso spazi multipli, Title Case tramite
	 *       {@code WordUtils.capitalizeFully()} con delimitatori spazio, trattino e apostrofo.</li>
	 *   <li><b>vatNumber:</b> rimozione di spazi, virgole, punti, trattini, slash e underscore,
	 *       conversione in uppercase.</li>
	 *   <li><b>legalAddress:</b> sostituzione CR/LF/TAB con spazi, collasso spazi multipli, trim.</li>
	 * </ul>
	 * L'accesso avviene tramite Java Reflection ({@code Method.setAccessible(true)}).
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("normalize() — Lifecycle Hook @PrePersist/@PreUpdate")
	class NormalizeLifecycleHook {

		/**
		 * Verifica il Happy Path completo di {@code normalize()}: tutti e tre i campi
		 * vengono normalizzati correttamente con dati gia' puliti. Il companyName viene
		 * convertito in Title Case, la vatNumber in uppercase senza punteggiatura,
		 * e il legalAddress viene trimato.
		 */
		@Test
		@DisplayName("Happy Path: normalizzazione completa con dati validi")
		void shouldNormalizeAllFieldsCorrectly() throws Exception {
			// Arrange
			customer.setCompanyName("acme logistics srl");
			customer.setVatNumber("IT 123.456.789-01");
			customer.setLegalAddress("Via Roma 10, 00100 Roma RM");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getCompanyName()).isEqualTo("Acme Logistics Srl");
			assertThat(customer.getVatNumber()).isEqualTo("IT12345678901");
			assertThat(customer.getLegalAddress()).isEqualTo("Via Roma 10, 00100 Roma RM");
		}

		/**
		 * Verifica che {@code companyName} con spazi multipli venga ridotto a spazi singoli
		 * e poi convertito in Title Case. Il metodo usa {@code replaceAll("\\s+", " ")} prima
		 * di passare a {@code WordUtils.capitalizeFully()}.
		 */
		@Test
		@DisplayName("companyName: spazi multipli collassati e Title Case applicato")
		void shouldCollapseMultipleSpacesInCompanyName() throws Exception {
			// Arrange
			customer.setCompanyName("  transport   italia   srl  ");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getCompanyName()).isEqualTo("Transport Italia Srl");
		}

		/**
		 * Verifica che il trattino sia riconosciuto come delimitatore da
		 * {@code WordUtils.capitalizeFully()}, capitalizzando la parola successiva.
		 * Esempio: "rossi-bianchi" deve diventare "Rossi-Bianchi".
		 */
		@Test
		@DisplayName("companyName: trattino come delimitatore per Title Case")
		void shouldCapitalizeAfterHyphenInCompanyName() throws Exception {
			// Arrange
			customer.setCompanyName("rossi-bianchi trasporti");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getCompanyName()).isEqualTo("Rossi-Bianchi Trasporti");
		}

		/**
		 * Verifica che l'apostrofo sia riconosciuto come delimitatore da
		 * {@code WordUtils.capitalizeFully()}, capitalizzando la parola successiva.
		 * Esempio: "l'azienda" deve diventare "L'Azienda".
		 */
		@Test
		@DisplayName("companyName: apostrofo come delimitatore per Title Case")
		void shouldCapitalizeAfterApostropheInCompanyName() throws Exception {
			// Arrange
			customer.setCompanyName("l'azienda del trasporto");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getCompanyName()).isEqualTo("L'Azienda Del Trasporto");
		}

		/**
		 * Verifica che la normalizzazione sia idempotente: invocare {@code normalize()}
		 * due volte consecutive deve produrre lo stesso risultato.
		 */
		@Test
		@DisplayName("normalize() deve essere idempotente")
		void shouldBeIdempotent() throws Exception {
			// Arrange
			customer.setCompanyName("  acme   logistics  srl  ");
			customer.setVatNumber("IT 123.456.789-01");
			customer.setLegalAddress("Via Roma\t10\n00100 Roma");

			// Act
			invokeNormalize();
			String firstCompanyName = customer.getCompanyName();
			String firstVatNumber = customer.getVatNumber();
			String firstAddress = customer.getLegalAddress();
			invokeNormalize();

			// Assert
			assertThat(customer.getCompanyName()).isEqualTo(firstCompanyName);
			assertThat(customer.getVatNumber()).isEqualTo(firstVatNumber);
			assertThat(customer.getLegalAddress()).isEqualTo(firstAddress);
		}

		/**
		 * Verifica che la {@code vatNumber} venga ripulita da tutti i caratteri di punteggiatura
		 * supportati dalla regex: spazi, virgole, punti, trattini, slash e underscore.
		 * Tutti devono essere rimossi e la stringa deve essere convertita in uppercase.
		 */
		@Test
		@DisplayName("vatNumber: rimozione di spazi, virgole, punti, trattini, slash e underscore")
		void shouldRemoveAllPunctuationFromVatNumber() throws Exception {
			// Arrange
			customer.setVatNumber("it 12,3.45-67/89_01");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getVatNumber()).isEqualTo("IT12345678901");
		}

		/**
		 * Verifica che la {@code vatNumber} interamente in minuscolo venga convertita
		 * in uppercase completo dalla normalizzazione.
		 */
		@Test
		@DisplayName("vatNumber: conversione lowercase a uppercase")
		void shouldConvertLowercaseVatNumberToUppercase() throws Exception {
			// Arrange
			customer.setVatNumber("de123456789");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getVatNumber()).isEqualTo("DE123456789");
		}

		/**
		 * Verifica che il {@code legalAddress} con caratteri di ritorno a capo (\\n),
		 * carriage return (\\r) e tabulazioni (\\t) venga appiattito su una singola riga
		 * con spazi singoli. Questo previene la rottura del layout nei documenti PDF.
		 */
		@Test
		@DisplayName("legalAddress: newline, carriage return e tab sostituiti con spazi")
		void shouldFlattenNewlinesAndTabsInLegalAddress() throws Exception {
			// Arrange
			customer.setLegalAddress("Via Roma 10\n00100 Roma\r\nItalia\tRM");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getLegalAddress()).isEqualTo("Via Roma 10 00100 Roma Italia RM");
		}

		/**
		 * Verifica che il {@code legalAddress} con lo stile CRLF di Windows (\\r\\n)
		 * venga correttamente appiattito su singola riga.
		 */
		@Test
		@DisplayName("legalAddress: CRLF Windows sostituiti con spazi")
		void shouldHandleWindowsCRLFInLegalAddress() throws Exception {
			// Arrange
			customer.setLegalAddress("Via Dante 5\r\n20121 Milano\r\nMI");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getLegalAddress()).isEqualTo("Via Dante 5 20121 Milano MI");
		}

		/**
		 * Verifica che spazi multipli nel {@code legalAddress} vengano collassati
		 * in un singolo spazio.
		 */
		@Test
		@DisplayName("legalAddress: spazi multipli collassati in spazio singolo")
		void shouldCollapseMultipleSpacesInLegalAddress() throws Exception {
			// Arrange
			customer.setLegalAddress("Via   Roma    10    00100   Roma");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getLegalAddress()).isEqualTo("Via Roma 10 00100 Roma");
		}

		/**
		 * Verifica che il {@code legalAddress} con spazi iniziali e finali venga
		 * correttamente trimato dopo la normalizzazione.
		 */
		@Test
		@DisplayName("legalAddress: trim degli spazi leading e trailing")
		void shouldTrimLegalAddress() throws Exception {
			// Arrange
			customer.setLegalAddress("   Via Roma 10, 00100 Roma RM   ");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getLegalAddress()).isEqualTo("Via Roma 10, 00100 Roma RM");
		}

		/**
		 * Verifica che la normalizzazione completa di un indirizzo incollato da un PDF
		 * (contenente un mix di newline, tab e spazi multipli) produca una singola riga pulita.
		 */
		@Test
		@DisplayName("legalAddress: normalizzazione di indirizzo incollato da PDF")
		void shouldNormalizePdfPastedAddress() throws Exception {
			// Arrange
			customer.setLegalAddress("\t  Via Roma  10\n\n\t00100\r\n  Roma\t\tRM  ");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getLegalAddress()).isEqualTo("Via Roma 10 00100 Roma RM");
		}

		/**
		 * Verifica che {@code companyName} composto da un singolo carattere venga
		 * comunque normalizzato correttamente in Title Case (boundary: lunghezza minima).
		 */
		@Test
		@DisplayName("companyName: singolo carattere normalizzato correttamente (boundary)")
		void shouldNormalizeSingleCharCompanyName() throws Exception {
			// Arrange
			customer.setCompanyName("x");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getCompanyName()).isEqualTo("X");
		}

		/**
		 * Verifica che una {@code vatNumber} gia' pulita e in uppercase non venga
		 * alterata dalla normalizzazione (nessuna trasformazione necessaria).
		 */
		@Test
		@DisplayName("vatNumber: stringa gia' normalizzata non viene alterata")
		void shouldNotAlterAlreadyCleanVatNumber() throws Exception {
			// Arrange
			customer.setVatNumber("IT12345678901");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getVatNumber()).isEqualTo("IT12345678901");
		}

		/**
		 * Verifica che {@code companyName} tutto in UPPERCASE venga convertito
		 * in Title Case da {@code capitalizeFully()}.
		 * Esempio: "ACME LOGISTICS SRL" diventa "Acme Logistics Srl".
		 */
		@Test
		@DisplayName("companyName: UPPERCASE convertito in Title Case")
		void shouldConvertUppercaseCompanyNameToTitleCase() throws Exception {
			// Arrange
			customer.setCompanyName("ACME LOGISTICS SRL");

			// Act
			invokeNormalize();

			// Assert
			assertThat(customer.getCompanyName()).isEqualTo("Acme Logistics Srl");
		}
	}

	// ========================================================================================
	// EQUALS
	// ========================================================================================

	/**
	 * Test del contratto {@code equals()} dell'entita' {@link Customer}.
	 * L'uguaglianza e' basata esclusivamente sulla Business Key naturale ({@code vatNumber}),
	 * in conformita' alle best practice di Hibernate che sconsigliano l'uso dell'ID surrogato
	 * generato dal database per la valutazione dell'identita'.
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("equals() — Contratto Business Key")
	class EqualsContract {

		/**
		 * Verifica la proprieta' di riflessivita': un oggetto deve essere uguale a se' stesso.
		 * Contratto fondamentale: {@code x.equals(x)} deve restituire {@code true}.
		 */
		@Test
		@DisplayName("Riflessivita': x.equals(x) deve essere true")
		void shouldBeReflexive() {
			// Arrange — customer gia' inizializzato con vatNumber

			// Act & Assert
			assertThat(customer.equals(customer)).isTrue();
		}

		/**
		 * Verifica la proprieta' di simmetria: se {@code a.equals(b)} e' {@code true},
		 * allora {@code b.equals(a)} deve essere {@code true}.
		 * Due Customer con lo stesso vatNumber ma dati diversi devono essere uguali.
		 */
		@Test
		@DisplayName("Simmetria: a.equals(b) implica b.equals(a)")
		void shouldBeSymmetric() {
			// Arrange
			Customer other = new Customer();
			other.setVatNumber("IT12345678901");
			other.setCompanyName("Nome Diverso Srl");

			// Act & Assert
			assertThat(customer.equals(other)).isTrue();
			assertThat(other.equals(customer)).isTrue();
		}

		/**
		 * Verifica la proprieta' di transitivita': se {@code a.equals(b)} e {@code b.equals(c)}
		 * sono entrambi {@code true}, allora {@code a.equals(c)} deve essere {@code true}.
		 */
		@Test
		@DisplayName("Transitivita': a.equals(b) && b.equals(c) implica a.equals(c)")
		void shouldBeTransitive() {
			// Arrange
			Customer b = new Customer();
			b.setVatNumber("IT12345678901");
			Customer c = new Customer();
			c.setVatNumber("IT12345678901");

			// Act & Assert
			assertThat(customer.equals(b)).isTrue();
			assertThat(b.equals(c)).isTrue();
			assertThat(customer.equals(c)).isTrue();
		}

		/**
		 * Verifica che {@code equals(null)} restituisca {@code false}.
		 * Un Customer non deve mai essere uguale a {@code null}.
		 */
		@Test
		@DisplayName("equals(null) deve restituire false")
		void shouldReturnFalseForNull() {
			// Arrange & Act & Assert
			assertThat(customer.equals(null)).isFalse();
		}

		/**
		 * Verifica che {@code equals()} restituisca {@code false} quando l'argomento
		 * e' un oggetto di tipo diverso, anche se casualmente ha un campo con lo stesso valore.
		 */
		@Test
		@DisplayName("equals() deve restituire false per tipo diverso")
		void shouldReturnFalseForDifferentType() {
			// Arrange
			String notACustomer = "IT12345678901";

			// Act & Assert
			assertThat(customer.equals(notACustomer)).isFalse();
		}

		/**
		 * Verifica che due Customer con {@code vatNumber} diversi siano considerati non uguali,
		 * anche se tutti gli altri campi sono identici.
		 */
		@Test
		@DisplayName("Due Customer con vatNumber diversi non devono essere uguali")
		void shouldNotBeEqualWithDifferentVatNumbers() {
			// Arrange
			Customer other = new Customer();
			other.setVatNumber("DE987654321");
			other.setCompanyName("Acme Logistics Srl");
			other.setLegalAddress("Via Roma 10, 00100 Roma RM");
			other.setActive(true);

			// Act & Assert
			assertThat(customer.equals(other)).isFalse();
		}

		/**
		 * Verifica che due Customer con lo stesso {@code vatNumber} ma ID diversi siano
		 * considerati uguali. L'ID surrogato NON partecipa al contratto di uguaglianza
		 * per prevenire anomalie con entita' transienti negli HashSet di Hibernate.
		 */
		@Test
		@DisplayName("Due Customer con stesso vatNumber ma ID diversi devono essere uguali")
		void shouldBeEqualWithSameVatNumberButDifferentIds() {
			// Arrange
			Customer other = new Customer();
			other.setId(999L);
			other.setVatNumber("IT12345678901");

			// Act & Assert
			assertThat(customer.equals(other)).isTrue();
		}

		/**
		 * Verifica che un Customer con {@code vatNumber = null} non sia uguale a un Customer
		 * con un vatNumber valorizzato. {@code Objects.equals(null, "IT12345678901")}
		 * deve restituire {@code false}.
		 */
		@Test
		@DisplayName("Customer con vatNumber null non deve essere uguale a Customer con vatNumber valorizzato")
		void shouldNotBeEqualWhenOneVatNumberIsNull() {
			// Arrange
			Customer nullVatCustomer = new Customer();
			nullVatCustomer.setVatNumber(null);

			// Act & Assert
			assertThat(nullVatCustomer.equals(customer)).isFalse();
		}
	}

	// ========================================================================================
	// HASHCODE
	// ========================================================================================

	/**
	 * Test del contratto {@code hashCode()} dell'entita' {@link Customer}.
	 * L'hash code e' calcolato esclusivamente sulla Business Key {@code vatNumber}
	 * tramite {@code Objects.hash(vatNumber)}, in coerenza con il contratto {@code equals()}.
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("hashCode() — Contratto Business Key")
	class HashCodeContract {

		/**
		 * Verifica la coerenza con {@code equals()}: se due Customer sono uguali per
		 * {@code equals()}, devono avere lo stesso {@code hashCode()}.
		 * Contratto fondamentale: {@code a.equals(b) → a.hashCode() == b.hashCode()}.
		 */
		@Test
		@DisplayName("Due Customer uguali devono avere lo stesso hashCode")
		void shouldHaveSameHashCodeWhenEqual() {
			// Arrange
			Customer other = new Customer();
			other.setVatNumber("IT12345678901");

			// Act & Assert
			assertThat(customer.hashCode()).isEqualTo(other.hashCode());
		}

		/**
		 * Verifica che due Customer con {@code vatNumber} diversi producano hashCode diversi.
		 * Nota: la disuguaglianza dell'hash non e' garantita dal contratto, ma e' un buon
		 * indicatore di distribuzione.
		 */
		@Test
		@DisplayName("Due Customer con vatNumber diversi dovrebbero avere hashCode diversi")
		void shouldHaveDifferentHashCodeForDifferentVatNumbers() {
			// Arrange
			Customer other = new Customer();
			other.setVatNumber("DE987654321");

			// Act & Assert
			assertThat(customer.hashCode()).isNotEqualTo(other.hashCode());
		}

		/**
		 * Verifica la proprieta' di stabilita': invocazioni multiple di {@code hashCode()}
		 * sullo stesso oggetto immutato devono restituire lo stesso valore.
		 */
		@Test
		@DisplayName("hashCode() deve essere stabile per invocazioni multiple")
		void shouldBeStableAcrossMultipleInvocations() {
			// Arrange
			int firstCall = customer.hashCode();

			// Act
			int secondCall = customer.hashCode();
			int thirdCall = customer.hashCode();

			// Assert
			assertThat(firstCall).isEqualTo(secondCall).isEqualTo(thirdCall);
		}

		/**
		 * Verifica che {@code hashCode()} non lanci eccezioni quando {@code vatNumber} e' null.
		 * {@code Objects.hash(null)} deve restituire un valore deterministico senza NPE.
		 */
		@Test
		@DisplayName("hashCode() con vatNumber null non deve lanciare eccezioni")
		void shouldNotThrowWhenVatNumberIsNull() {
			// Arrange
			customer.setVatNumber(null);

			// Act & Assert
			assertThatCode(() -> customer.hashCode()).doesNotThrowAnyException();
		}
	}

	// ========================================================================================
	// TOSTRING
	// ========================================================================================

	/**
	 * Test del metodo {@code toString()} dell'entita' {@link Customer}, che utilizza
	 * un {@code StringBuilder} per produrre una rappresentazione leggibile contenente
	 * tutti i campi dell'entita' nel formato
	 * {@code Customer [id=X, companyName=Y, vatNumber=Z, legalAddress=W, active=V]}.
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("toString()")
	class ToStringContract {

		/**
		 * Verifica che {@code toString()} contenga tutti i campi chiave dell'entita':
		 * id, companyName, vatNumber, legalAddress e active.
		 */
		@Test
		@DisplayName("toString() deve contenere tutti i campi dell'entita'")
		void shouldContainAllFields() {
			// Arrange — customer gia' inizializzato

			// Act
			String result = customer.toString();

			// Assert
			assertThat(result)
				.contains("id=1")
				.contains("companyName=Acme Logistics Srl")
				.contains("vatNumber=IT12345678901")
				.contains("legalAddress=Via Roma 10, 00100 Roma RM")
				.contains("active=true");
		}

		/**
		 * Verifica il formato esatto della stringa prodotta da {@code toString()},
		 * confermando il pattern {@code Customer [id=..., companyName=..., ...]}.
		 */
		@Test
		@DisplayName("toString() deve avere il formato esatto atteso")
		void shouldMatchExactFormat() {
			// Arrange — customer gia' inizializzato

			// Act
			String result = customer.toString();

			// Assert
			assertThat(result).isEqualTo(
				"Customer [id=1, companyName=Acme Logistics Srl, vatNumber=IT12345678901, " +
				"legalAddress=Via Roma 10, 00100 Roma RM, active=true]"
			);
		}

		/**
		 * Verifica che {@code toString()} gestisca correttamente un'entita' transiente
		 * con tutti i campi a null/default senza lanciare eccezioni.
		 */
		@Test
		@DisplayName("toString() non deve lanciare eccezioni per entita' transiente (campi null)")
		void shouldHandleTransientEntityWithNullFields() {
			// Arrange
			Customer transient_ = new Customer();

			// Act
			String result = transient_.toString();

			// Assert
			assertThat(result)
				.contains("id=null")
				.contains("companyName=null")
				.contains("vatNumber=null")
				.contains("legalAddress=null")
				.contains("active=false");
		}

		/**
		 * Verifica che la stringa restituita da {@code toString()} inizi con il prefisso
		 * "Customer [" e termini con "]".
		 */
		@Test
		@DisplayName("toString() deve iniziare con 'Customer [' e terminare con ']'")
		void shouldHaveCorrectPrefixAndSuffix() {
			// Arrange — customer gia' inizializzato

			// Act
			String result = customer.toString();

			// Assert
			assertThat(result).startsWith("Customer [").endsWith("]");
		}
	}

	// ========================================================================================
	// COLLECTION INTEGRATION (HashSet, HashMap)
	// ========================================================================================

	/**
	 * Test di integrazione di {@link Customer} con le Collection Java ({@code HashSet},
	 * {@code HashMap}). Verifica che il contratto {@code equals()}/{@code hashCode()} basato
	 * sulla Business Key {@code vatNumber} funzioni correttamente quando le istanze
	 * sono inserite in strutture dati hash-based, come avviene nella gestione delle
	 * Collection di Hibernate.
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("Integrazione con Collection (HashSet, HashMap)")
	class CollectionIntegration {

		/**
		 * Verifica che un {@code HashSet} non inserisca duplicati di Customer con lo
		 * stesso {@code vatNumber}. Due istanze Java distinte con la stessa Business Key
		 * devono essere considerate un unico elemento nel Set.
		 */
		@Test
		@DisplayName("HashSet non deve inserire duplicati con stesso vatNumber")
		void shouldDeduplicateInHashSet() {
			// Arrange
			Customer duplicate = new Customer();
			duplicate.setVatNumber("IT12345678901");
			duplicate.setCompanyName("Nome Completamente Diverso");
			Set<Customer> set = new HashSet<>();

			// Act
			set.add(customer);
			set.add(duplicate);

			// Assert
			assertThat(set).hasSize(1);
		}

		/**
		 * Verifica che un {@code HashSet} inserisca correttamente Customer con
		 * {@code vatNumber} diversi come elementi distinti.
		 */
		@Test
		@DisplayName("HashSet deve inserire Customer con vatNumber diversi come distinti")
		void shouldInsertDistinctCustomersInHashSet() {
			// Arrange
			Customer other = new Customer();
			other.setVatNumber("DE987654321");
			Set<Customer> set = new HashSet<>();

			// Act
			set.add(customer);
			set.add(other);

			// Assert
			assertThat(set).hasSize(2);
		}

		/**
		 * Verifica che {@code HashSet.contains()} trovi un Customer usando un'altra
		 * istanza con lo stesso {@code vatNumber}. Questo e' fondamentale per il
		 * corretto funzionamento della cache in-memory e della deduplicazione nel
		 * Service Layer.
		 */
		@Test
		@DisplayName("HashSet.contains() deve trovare Customer con stesso vatNumber")
		void shouldFindCustomerByVatNumberInHashSet() {
			// Arrange
			Set<Customer> set = new HashSet<>();
			set.add(customer);
			Customer lookup = new Customer();
			lookup.setVatNumber("IT12345678901");

			// Act & Assert
			assertThat(set.contains(lookup)).isTrue();
		}

		/**
		 * Verifica che un {@code HashMap} con chiave {@link Customer} mappi correttamente
		 * il valore quando la lookup avviene con un'istanza diversa avente lo stesso
		 * {@code vatNumber}.
		 */
		@Test
		@DisplayName("HashMap deve risolvere la chiave Customer usando la Business Key")
		void shouldResolveLookupInHashMap() {
			// Arrange
			Map<Customer, String> map = new HashMap<>();
			map.put(customer, "SENDER");
			Customer lookup = new Customer();
			lookup.setVatNumber("IT12345678901");

			// Act
			String role = map.get(lookup);

			// Assert
			assertThat(role).isEqualTo("SENDER");
		}

		/**
		 * Verifica che {@code HashSet.remove()} rimuova un Customer usando un'altra
		 * istanza con lo stesso {@code vatNumber}.
		 */
		@Test
		@DisplayName("HashSet.remove() deve rimuovere Customer con stesso vatNumber")
		void shouldRemoveByBusinessKeyFromHashSet() {
			// Arrange
			Set<Customer> set = new HashSet<>();
			set.add(customer);
			Customer toRemove = new Customer();
			toRemove.setVatNumber("IT12345678901");

			// Act
			boolean removed = set.remove(toRemove);

			// Assert
			assertThat(removed).isTrue();
			assertThat(set).isEmpty();
		}
	}

	// ========================================================================================
	// RED TDD — VULNERABILITA' E VALIDAZIONI MANCANTI
	// ========================================================================================

	/**
	 * Test in <b>FASE RED TDD</b> deliberata. Questi test sono scritti appositamente per
	 * <b>FALLIRE</b> con il codice sorgente attuale, esponendo vulnerabilita' architetturali
	 * e validazioni mancanti nell'entita' {@link Customer}. Lo sviluppatore deve implementare
	 * le correzioni indicate per portare ciascun test in FASE GREEN.
	 * <p>
	 * <b>Vulnerabilita' coperte:</b>
	 * <ul>
	 *   <li>{@code normalize()} causa NullPointerException per {@code companyName} null</li>
	 *   <li>{@code normalize()} causa NullPointerException per {@code vatNumber} null</li>
	 *   <li>{@code normalize()} causa NullPointerException per {@code legalAddress} null</li>
	 *   <li>{@code normalize()} accetta {@code companyName} composto da soli spazi (post-trim vuoto)</li>
	 *   <li>{@code normalize()} accetta {@code vatNumber} composto da soli caratteri punteggiatura</li>
	 *   <li>{@code normalize()} accetta {@code legalAddress} composto da soli whitespace</li>
	 *   <li>Assenza di validazione della lunghezza della {@code vatNumber}</li>
	 *   <li>{@code equals()} considera uguali due entita' con {@code vatNumber = null}</li>
	 * </ul>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("[TDD-RED] Vulnerabilita' e Validazioni Mancanti")
	class RedTddVulnerabilities {

		/**
		 * <b>[TDD-RED] Vulnerabilita' CRITICA:</b> {@code normalize()} invoca
		 * {@code companyName.trim()} senza verificare che {@code companyName} sia non-null.
		 * Se un Customer viene persistito con {@code companyName = null}, il lifecycle hook
		 * JPA {@code @PrePersist} causa una {@link NullPointerException} non gestita,
		 * che si propaga come {@code PersistenceException} opaca a livello di servizio.
		 * <p>
		 * <b>Comportamento atteso:</b> Il metodo {@code normalize()} deve lanciare
		 * {@link IllegalArgumentException} con un messaggio diagnostico chiaro
		 * prima di accedere ai campi null.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere come prima istruzione di {@code normalize()}:
		 * {@code if (companyName == null) throw new IllegalArgumentException("companyName cannot be null");}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] normalize() deve lanciare IllegalArgumentException per companyName null")
		void shouldThrowWhenCompanyNameIsNull_RED() {
			// Arrange
			customer.setCompanyName(null);

			// Act & Assert
			assertThatThrownBy(() -> invokeNormalize())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("companyName");
		}

		/**
		 * <b>[TDD-RED] Vulnerabilita' CRITICA:</b> {@code normalize()} invoca
		 * {@code vatNumber.replaceAll()} senza verificare che {@code vatNumber} sia non-null.
		 * Se un Customer viene persistito con {@code vatNumber = null}, il lifecycle hook
		 * JPA causa una {@link NullPointerException} non gestita.
		 * <p>
		 * <b>Comportamento atteso:</b> Il metodo {@code normalize()} deve lanciare
		 * {@link IllegalArgumentException} prima di operare su {@code vatNumber} null.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere guard:
		 * {@code if (vatNumber == null) throw new IllegalArgumentException("vatNumber cannot be null");}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] normalize() deve lanciare IllegalArgumentException per vatNumber null")
		void shouldThrowWhenVatNumberIsNull_RED() {
			// Arrange
			customer.setVatNumber(null);

			// Act & Assert
			assertThatThrownBy(() -> invokeNormalize())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("vatNumber");
		}

		/**
		 * <b>[TDD-RED] Vulnerabilita' CRITICA:</b> {@code normalize()} invoca
		 * {@code legalAddress.replaceAll()} senza verificare che {@code legalAddress} sia
		 * non-null. Se un Customer viene persistito con {@code legalAddress = null},
		 * il lifecycle hook JPA causa una {@link NullPointerException} non gestita.
		 * <p>
		 * <b>Comportamento atteso:</b> Il metodo {@code normalize()} deve lanciare
		 * {@link IllegalArgumentException} prima di operare su {@code legalAddress} null.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere guard:
		 * {@code if (legalAddress == null) throw new IllegalArgumentException("legalAddress cannot be null");}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] normalize() deve lanciare IllegalArgumentException per legalAddress null")
		void shouldThrowWhenLegalAddressIsNull_RED() {
			// Arrange
			customer.setLegalAddress(null);

			// Act & Assert
			assertThatThrownBy(() -> invokeNormalize())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("legalAddress");
		}

		/**
		 * <b>[TDD-RED] Vulnerabilita' ALTA:</b> {@code normalize()} accetta un
		 * {@code companyName} composto da soli spazi. Dopo {@code trim()} e
		 * {@code replaceAll("\\s+", " ")}, il campo diventa una stringa vuota {@code ""}
		 * che viene passata a {@code WordUtils.capitalizeFully("")} producendo {@code ""}.
		 * Una ragione sociale vuota viola il vincolo semantico del dominio (un'azienda
		 * deve avere un nome) ma non genera eccezioni, permettendo la persistenza di
		 * record corrotti.
		 * <p>
		 * <b>Comportamento atteso:</b> {@code normalize()} deve lanciare
		 * {@link IllegalArgumentException} se {@code companyName} risulta blank dopo il trim.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Dopo il trim in {@code normalize()}:
		 * {@code if (companyName.isBlank()) throw new IllegalArgumentException("companyName cannot be blank after normalization");}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] normalize() deve lanciare eccezione per companyName di soli spazi")
		void shouldThrowWhenCompanyNameIsBlankOnly_RED() throws Exception {
			// Arrange
			customer.setCompanyName("     ");

			// Act & Assert
			assertThatThrownBy(() -> invokeNormalize())
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[TDD-RED] Vulnerabilita' ALTA:</b> {@code normalize()} accetta un
		 * {@code vatNumber} composto esclusivamente da caratteri di punteggiatura
		 * (spazi, virgole, punti, trattini, slash, underscore). La regex
		 * {@code "[\\s,\\.\\-/_]+"} rimuove tutti i caratteri, producendo una stringa
		 * vuota {@code ""} che viene poi convertita in uppercase. Una Partita IVA vuota
		 * non e' un codice fiscale valido in nessuna giurisdizione europea ma viene
		 * accettata silenziosamente, permettendo la persistenza di record corrotti.
		 * <p>
		 * <b>Comportamento atteso:</b> {@code normalize()} deve lanciare
		 * {@link IllegalArgumentException} se {@code vatNumber} risulta vuota dopo la pulizia.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Dopo la regex in {@code normalize()}:
		 * {@code if (vatNumber.isBlank()) throw new IllegalArgumentException("vatNumber cannot be empty after normalization");}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] normalize() deve lanciare eccezione per vatNumber di sola punteggiatura")
		void shouldThrowWhenVatNumberIsOnlyPunctuation_RED() throws Exception {
			// Arrange
			customer.setVatNumber("  ,  .  -  /  _  ");

			// Act & Assert
			assertThatThrownBy(() -> invokeNormalize())
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[TDD-RED] Vulnerabilita' ALTA:</b> {@code normalize()} accetta un
		 * {@code legalAddress} composto esclusivamente da caratteri di whitespace
		 * (spazi, tab, newline). Dopo la sostituzione dei control characters e il trim,
		 * il campo diventa una stringa vuota {@code ""}. Un indirizzo legale vuoto
		 * compromette la generazione del DDT e il geocoding HeiGIT a valle.
		 * <p>
		 * <b>Comportamento atteso:</b> {@code normalize()} deve lanciare
		 * {@link IllegalArgumentException} se {@code legalAddress} risulta blank dopo la pulizia.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Dopo il trim in {@code normalize()}:
		 * {@code if (legalAddress.isBlank()) throw new IllegalArgumentException("legalAddress cannot be blank after normalization");}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] normalize() deve lanciare eccezione per legalAddress di soli whitespace")
		void shouldThrowWhenLegalAddressIsOnlyWhitespace_RED() throws Exception {
			// Arrange
			customer.setLegalAddress("  \t  \n  \r\n  ");

			// Act & Assert
			assertThatThrownBy(() -> invokeNormalize())
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[TDD-RED] Vulnerabilita' MEDIA:</b> {@code normalize()} non valida la lunghezza
		 * della {@code vatNumber} post-normalizzazione. Il campo JPA ha un vincolo
		 * {@code @Column(length = 30)}, ma il metodo {@code normalize()} non verifica
		 * che la stringa risultante non ecceda questo limite. Una vatNumber di 31+ caratteri
		 * supera la normalizzazione senza errore applicativo, causando un
		 * {@code DataException} opaco a livello JDBC/MariaDB.
		 * <p>
		 * <b>Comportamento atteso:</b> {@code normalize()} deve lanciare
		 * {@link IllegalArgumentException} se la {@code vatNumber} normalizzata supera
		 * 30 caratteri.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Dopo la normalizzazione in {@code normalize()}:
		 * {@code if (vatNumber.length() > 30) throw new IllegalArgumentException("vatNumber exceeds max length of 30 characters");}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] normalize() deve lanciare eccezione per vatNumber oltre 30 caratteri")
		void shouldThrowWhenVatNumberExceedsMaxLength_RED() throws Exception {
			// Arrange — 31 caratteri alfanumerici validi
			customer.setVatNumber("IT1234567890123456789012345678X");

			// Assert — il campo normalizzato eccede i 30 caratteri del @Column(length = 30)
			assertThatThrownBy(() -> invokeNormalize())
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[TDD-RED] Vulnerabilita' MEDIA:</b> {@code normalize()} non valida la lunghezza
		 * della {@code companyName} post-normalizzazione. Il campo JPA ha un vincolo
		 * {@code @Column(length = 255)}, ma il metodo non verifica il rispetto di questo
		 * limite. Una ragione sociale di 256+ caratteri causa un {@code DataException}
		 * opaco a livello JDBC.
		 * <p>
		 * <b>Comportamento atteso:</b> {@code normalize()} deve lanciare
		 * {@link IllegalArgumentException} se la {@code companyName} normalizzata supera
		 * 255 caratteri.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Dopo la normalizzazione in {@code normalize()}:
		 * {@code if (companyName.length() > 255) throw new IllegalArgumentException("companyName exceeds max length of 255 characters");}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] normalize() deve lanciare eccezione per companyName oltre 255 caratteri")
		void shouldThrowWhenCompanyNameExceedsMaxLength_RED() throws Exception {
			// Arrange — 256 caratteri
			customer.setCompanyName("A".repeat(256));

			// Act & Assert
			assertThatThrownBy(() -> invokeNormalize())
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[TDD-RED] Vulnerabilita' MEDIA:</b> {@code normalize()} non valida la lunghezza
		 * del {@code legalAddress} post-normalizzazione. Il campo JPA ha un vincolo
		 * {@code @Column(length = 255)}, ma il metodo non verifica il rispetto di questo
		 * limite.
		 * <p>
		 * <b>Comportamento atteso:</b> {@code normalize()} deve lanciare
		 * {@link IllegalArgumentException} se il {@code legalAddress} normalizzato supera
		 * 255 caratteri.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Dopo la normalizzazione in {@code normalize()}:
		 * {@code if (legalAddress.length() > 255) throw new IllegalArgumentException("legalAddress exceeds max length of 255 characters");}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] normalize() deve lanciare eccezione per legalAddress oltre 255 caratteri")
		void shouldThrowWhenLegalAddressExceedsMaxLength_RED() throws Exception {
			// Arrange — 256 caratteri
			customer.setLegalAddress("V".repeat(256));

			// Act & Assert
			assertThatThrownBy(() -> invokeNormalize())
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[TDD-RED] Vulnerabilita' MEDIA:</b> {@code equals()} considera uguali due
		 * entita' con {@code vatNumber = null}. {@code Objects.equals(null, null)} restituisce
		 * {@code true}. Due Customer in stato transiente (senza Business Key) vengono
		 * considerati identici, causando sovrascrizioni inattese in cache, falsi positivi
		 * nella deduplicazione e comportamenti anomali in {@code HashSet} e {@code HashMap}
		 * di Hibernate.
		 * <p>
		 * <b>Comportamento atteso:</b> {@code equals()} deve restituire {@code false}
		 * quando entrambe le istanze hanno {@code vatNumber = null}, poiche' due entita'
		 * prive di Business Key non hanno un'identita' deterministica.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere in {@code equals()}, prima del confronto:
		 * {@code if (this.vatNumber == null) return false;}
		 * </p>
		 */
		@Test
		@DisplayName("[RED] equals() deve restituire false quando entrambi i vatNumber sono null")
		void shouldReturnFalseWhenBothVatNumbersAreNull_RED() {
			// Arrange
			Customer a = new Customer();
			a.setVatNumber(null);
			Customer b = new Customer();
			b.setVatNumber(null);

			// Act & Assert
			assertThat(a.equals(b)).isNotEqualTo(true);
		}
	}
}
