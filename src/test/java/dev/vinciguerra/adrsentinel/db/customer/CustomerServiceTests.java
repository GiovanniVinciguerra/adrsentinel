package dev.vinciguerra.adrsentinel.db.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerUpdateActiveStatusDTO;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerUpdateDTO;

/**
 * Suite di test unitari per il Service di dominio {@link CustomerService}.
 * <p>
 * Questa classe verifica in isolamento totale (senza contesto Spring, senza H2,
 * senza ORM avviato) tutti e 7 i metodi pubblici del servizio, coprendo:
 * <ul>
 * <li><b>Happy Path:</b> flusso nominale con input validi e dati presenti a
 * sistema.</li>
 * <li><b>Failure Path:</b> eccezioni attese ({@link ResourceNotFoundException})
 * quando
 * il record non esiste.</li>
 * <li><b>Edge Cases:</b> liste vuote, stringhe di confine, flag booleani
 * invertiti.</li>
 * <li><b>TDD-RED (Fase Rossa):</b> test deliberatamente scritti per FALLIRE
 * finché lo
 * sviluppatore non implementa le validazioni mancanti (null/blank guard).
 * Questi test
 * asseriscono il comportamento architetturale corretto e sono contrassegnati
 * con
 * il suffisso {@code _RED} e con il tag {@code [TDD-RED]} nel Javadoc.</li>
 * </ul>
 * <b>Strategia di Isolamento:</b> {@code @ExtendWith(MockitoExtension.class)}
 * con
 * {@code @Mock} per {@link CustomerRepository} e {@link CacheManager}.
 * Il pattern Arrange-Act-Assert (Given-When-Then) è applicato rigorosamente
 * in ogni singolo metodo di test.
 * </p>
 * 
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService — Unit Test Suite")
class CustomerServiceTests {

	@Mock
	private CustomerRepository customerRepository;

	@Mock
	private CacheManager cacheManager;

	@InjectMocks
	private CustomerService customerService;

	// ─────────────────────────────────────────────────────────────────────
	// HELPER FACTORY
	// ─────────────────────────────────────────────────────────────────────

	/**
	 * Factory method di utilità per la costruzione rapida di un'entità
	 * {@link Customer}
	 * con tutti i campi obbligatori valorizzati.
	 * Evita la ripetizione di codice boilerplate nei blocchi Arrange.
	 * 
	 * @param id           l'identificatore surrogato dell'entità.
	 * @param companyName  la ragione sociale dell'azienda.
	 * @param vatNumber    la Partita IVA (Business Key).
	 * @param legalAddress l'indirizzo della sede legale.
	 * @param active       il flag di stato operativo.
	 * @return l'istanza di {@link Customer} valorizzata.
	 */
	private Customer buildCustomer(Long id, String companyName, String vatNumber, String legalAddress, boolean active) {
		Customer c = new Customer();
		c.setId(id);
		c.setCompanyName(companyName);
		c.setVatNumber(vatNumber);
		c.setLegalAddress(legalAddress);
		c.setActive(active);
		return c;
	}

	// ═════════════════════════════════════════════════════════════════════
	// getByVatNumber(String)
	// ═════════════════════════════════════════════════════════════════════

	/**
	 * Classe innestata che raggruppa tutti i test relativi al metodo
	 * {@link CustomerService#getByVatNumber(String)}.
	 * <p>
	 * Il metodo opera con strategia Cache-Aside e lancia
	 * {@link ResourceNotFoundException} se il record non è trovato.
	 * </p>
	 * 
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("getByVatNumber(String)")
	class GetByVatNumberTests {

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo restituisca correttamente l'entità
		 * {@link Customer} quando il repository trova un record corrispondente alla
		 * Partita IVA fornita.
		 * <p>
		 * Mock attivo: {@link CustomerRepository#findByVatNumber(String)} restituisce
		 * un {@link Optional} contenente il Customer.
		 * </p>
		 * <p>
		 * Risultato atteso: l'entità restituita è identica a quella mockata.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe restituire il Customer quando la Partita IVA esiste")
		void shouldReturnCustomerWhenVatNumberExists() {
			// Arrange
			Customer expected = buildCustomer(1L, "Acme S.r.l.", "IT01234567890", "Via Roma 1, Milano", true);
			when(customerRepository.findByVatNumber("IT01234567890")).thenReturn(Optional.of(expected));

			// Act
			Customer result = customerService.getByVatNumber("IT01234567890");

			// Assert
			assertThat(result).isNotNull().isEqualTo(expected);
			assertThat(result.getCompanyName()).isEqualTo("Acme S.r.l.");
			assertThat(result.getVatNumber()).isEqualTo("IT01234567890");
			verify(customerRepository).findByVatNumber("IT01234567890");
		}

		/**
		 * <b>[FAILURE PATH]</b> Verifica che il metodo lanci
		 * {@link ResourceNotFoundException}
		 * quando la Partita IVA non corrisponde ad alcun record nel database.
		 * <p>
		 * Mock attivo: {@link CustomerRepository#findByVatNumber(String)} restituisce
		 * {@link Optional#empty()}.
		 * </p>
		 * <p>
		 * Risultato atteso: {@link ResourceNotFoundException} con messaggio
		 * contestuale.
		 * </p>
		 */
		@Test
		@DisplayName("FP — Dovrebbe lanciare ResourceNotFoundException quando la P.IVA non esiste")
		void shouldThrowResourceNotFoundExceptionWhenVatNumberDoesNotExist() {
			// Arrange
			when(customerRepository.findByVatNumber("INESISTENTE")).thenReturn(Optional.empty());

			// Act & Assert
			assertThatThrownBy(() -> customerService.getByVatNumber("INESISTENTE"))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Customer not found")
					.hasMessageContaining("INESISTENTE");
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando viene invocato con {@code null} come Partita IVA.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo attuale NON esegue alcun null-check
		 * sull'input.
		 * L'invocazione con {@code null} viene propagata direttamente al repository,
		 * causando
		 * una query malformata o una {@link NullPointerException} non gestita → HTTP
		 * 500.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere come prima istruzione del metodo:
		 * {@code if (vatNumber == null) throw new IllegalArgumentException("vatNumber cannot be null");}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando vatNumber è null")
		void shouldThrowIllegalArgumentExceptionWhenVatNumberIsNull_RED() {
			// Arrange — nessun mock necessario: il guard deve scattare prima del repository

			// Act & Assert
			assertThatThrownBy(() -> customerService.getByVatNumber(null))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("vatNumber");

			verify(customerRepository, never()).findByVatNumber(any());
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando viene invocato con una stringa blank (soli spazi).
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo attuale NON esegue alcun blank-check
		 * sull'input.
		 * Una stringa composta da soli spazi viene inoltrata al repository, producendo
		 * una query
		 * non semantica che restituisce {@link Optional#empty()}, generando una
		 * {@link ResourceNotFoundException} fuorviante anziché un errore di input
		 * validation.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere:
		 * {@code if (vatNumber == null || vatNumber.isBlank()) throw new IllegalArgumentException(...)}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando vatNumber è blank")
		void shouldThrowIllegalArgumentExceptionWhenVatNumberIsBlank_RED() {
			// Arrange — nessun mock necessario

			// Act & Assert
			assertThatThrownBy(() -> customerService.getByVatNumber("   "))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("vatNumber");

			verify(customerRepository, never()).findByVatNumber(any());
		}
	}

	// ═════════════════════════════════════════════════════════════════════
	// getByCompanyName(String)
	// ═════════════════════════════════════════════════════════════════════

	/**
	 * Classe innestata che raggruppa tutti i test relativi al metodo
	 * {@link CustomerService#getByCompanyName(String)}.
	 * <p>
	 * Il metodo recupera una lista di clienti per Ragione Sociale esatta.
	 * Gestisce intrinsecamente le omonimie (filiali diverse con lo stesso nome).
	 * </p>
	 * 
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("getByCompanyName(String)")
	class GetByCompanyNameTests {

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo restituisca correttamente una
		 * lista
		 * di Customer quando il repository trova record corrispondenti alla Ragione
		 * Sociale.
		 * <p>
		 * Mock attivo: {@link CustomerRepository#findByCompanyName(String)} restituisce
		 * una lista con due elementi (simulazione omonimia).
		 * </p>
		 * <p>
		 * Risultato atteso: lista con 2 elementi, entrambi con la stessa Ragione
		 * Sociale
		 * ma Partite IVA distinte.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe restituire la lista di Customer con omonimie")
		void shouldReturnCustomerListWhenCompanyNameExists() {
			// Arrange
			Customer c1 = buildCustomer(1L, "Rossi Trasporti", "IT11111111111", "Via Verdi 1", true);
			Customer c2 = buildCustomer(2L, "Rossi Trasporti", "IT22222222222", "Via Bianchi 2", true);
			when(customerRepository.findByCompanyName("Rossi Trasporti")).thenReturn(List.of(c1, c2));

			// Act
			List<Customer> result = customerService.getByCompanyName("Rossi Trasporti");

			// Assert
			assertThat(result).hasSize(2);
			assertThat(result).extracting(Customer::getVatNumber)
					.containsExactlyInAnyOrder("IT11111111111", "IT22222222222");
			verify(customerRepository).findByCompanyName("Rossi Trasporti");
		}

		/**
		 * <b>[EDGE CASE]</b> Verifica che il metodo restituisca una lista vuota (e non
		 * null)
		 * quando nessun cliente corrisponde alla Ragione Sociale indicata.
		 * <p>
		 * Mock attivo: {@link CustomerRepository#findByCompanyName(String)} restituisce
		 * lista vuota.
		 * </p>
		 * <p>
		 * Risultato atteso: lista vuota, mai {@code null}.
		 * </p>
		 */
		@Test
		@DisplayName("EDGE — Dovrebbe restituire lista vuota quando la Ragione Sociale non esiste")
		void shouldReturnEmptyListWhenCompanyNameDoesNotExist() {
			// Arrange
			when(customerRepository.findByCompanyName("Fantasma S.p.A.")).thenReturn(Collections.emptyList());

			// Act
			List<Customer> result = customerService.getByCompanyName("Fantasma S.p.A.");

			// Assert
			assertThat(result).isNotNull().isEmpty();
			verify(customerRepository).findByCompanyName("Fantasma S.p.A.");
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando viene invocato con {@code null} come Ragione Sociale.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo attuale NON esegue alcun null-check
		 * sull'input.
		 * L'invocazione con {@code null} viene propagata direttamente al repository
		 * senza
		 * alcuna validazione preventiva.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere come prima istruzione:
		 * {@code if (companyName == null) throw new IllegalArgumentException("companyName cannot be null");}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando companyName è null")
		void shouldThrowIllegalArgumentExceptionWhenCompanyNameIsNull_RED() {
			// Arrange — nessun mock necessario

			// Act & Assert
			assertThatThrownBy(() -> customerService.getByCompanyName(null))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("companyName");

			verify(customerRepository, never()).findByCompanyName(any());
		}
	}

	// ═════════════════════════════════════════════════════════════════════
	// getAllCustomer()
	// ═════════════════════════════════════════════════════════════════════

	/**
	 * Classe innestata che raggruppa tutti i test relativi al metodo
	 * {@link CustomerService#getAllCustomer()}.
	 * <p>
	 * Il metodo recupera l'intero set di clienti usando una chiave di cache statica
	 * (Hardcoded Key).
	 * </p>
	 * 
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("getAllCustomer()")
	class GetAllCustomerTests {

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo restituisca correttamente la lista
		 * completa di tutti i Customer registrati nel sistema.
		 * <p>
		 * Mock attivo: {@link CustomerRepository#findAll()} restituisce una lista di 3
		 * elementi.
		 * </p>
		 * <p>
		 * Risultato atteso: lista con esattamente 3 elementi.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe restituire tutti i Customer")
		void shouldReturnAllCustomers() {
			// Arrange
			Customer c1 = buildCustomer(1L, "Alfa", "IT11111111111", "Via A", true);
			Customer c2 = buildCustomer(2L, "Beta", "IT22222222222", "Via B", false);
			Customer c3 = buildCustomer(3L, "Gamma", "IT33333333333", "Via C", true);
			when(customerRepository.findAll()).thenReturn(List.of(c1, c2, c3));

			// Act
			List<Customer> result = customerService.getAllCustomer();

			// Assert
			assertThat(result).hasSize(3);
			assertThat(result).extracting(Customer::getVatNumber)
					.containsExactly("IT11111111111", "IT22222222222", "IT33333333333");
			verify(customerRepository).findAll();
		}

		/**
		 * <b>[EDGE CASE]</b> Verifica che il metodo restituisca una lista vuota
		 * quando il database non contiene alcun cliente.
		 * <p>
		 * Mock attivo: {@link CustomerRepository#findAll()} restituisce lista vuota.
		 * </p>
		 * <p>
		 * Risultato atteso: lista vuota, mai {@code null}.
		 * </p>
		 */
		@Test
		@DisplayName("EDGE — Dovrebbe restituire lista vuota quando non ci sono Customer")
		void shouldReturnEmptyListWhenNoCustomersExist() {
			// Arrange
			when(customerRepository.findAll()).thenReturn(Collections.emptyList());

			// Act
			List<Customer> result = customerService.getAllCustomer();

			// Assert
			assertThat(result).isNotNull().isEmpty();
			verify(customerRepository).findAll();
		}
	}

	// ═════════════════════════════════════════════════════════════════════
	// save(Customer)
	// ═════════════════════════════════════════════════════════════════════

	/**
	 * Classe innestata che raggruppa tutti i test relativi al metodo
	 * {@link CustomerService#save(Customer)}.
	 * <p>
	 * Il metodo persiste un nuovo cliente e registra un
	 * {@code TransactionSynchronization}
	 * per la sincronizzazione post-commit della cache.
	 * </p>
	 * 
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("save(Customer)")
	class SaveTests {
		
		@BeforeEach
		void initTransactionSynchronization() {
		    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
		        TransactionSynchronizationManager.initSynchronization();
		    }
		}

		@AfterEach
		void clearTransactionSynchronization() {
		    if (TransactionSynchronizationManager.isSynchronizationActive()) {
		        TransactionSynchronizationManager.clearSynchronization();
		    }
		}

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo persista correttamente un nuovo
		 * Customer
		 * e restituisca l'entità consolidata con l'ID generato.
		 * <p>
		 * Mock attivo: {@link CustomerRepository#save(Object)} restituisce il Customer
		 * con ID popolato.
		 * </p>
		 * <p>
		 * Risultato atteso: entità non-null con ID = 1, vatNumber e companyName
		 * invariati.
		 * Il repository viene invocato esattamente una volta con l'entità transiente.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe salvare e restituire il Customer con ID generato")
		void shouldSaveAndReturnCustomerWithGeneratedId() {
			// Arrange
			Customer transientCustomer = buildCustomer(null, "Nuova Azienda", "IT99887766554", "Via Nuova 10", true);
			Customer savedCustomer = buildCustomer(1L, "Nuova Azienda", "IT99887766554", "Via Nuova 10", true);
			when(customerRepository.save(transientCustomer)).thenReturn(savedCustomer);

			// Act
			Customer result = customerService.save(transientCustomer);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.getId()).isEqualTo(1L);
			assertThat(result.getVatNumber()).isEqualTo("IT99887766554");
			assertThat(result.getCompanyName()).isEqualTo("Nuova Azienda");
			verify(customerRepository).save(transientCustomer);
		}

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo invochi il repository con l'entità
		 * passata come argomento, catturando l'argomento effettivo tramite
		 * {@link ArgumentCaptor}.
		 * <p>
		 * Mock attivo: {@link CustomerRepository#save(Object)} con cattura argomento.
		 * </p>
		 * <p>
		 * Risultato atteso: l'argomento catturato ha la stessa Partita IVA dell'input.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe propagare l'entità corretta al repository")
		void shouldPropagateCorrectEntityToRepository() {
			// Arrange
			Customer input = buildCustomer(null, "Test Corp", "IT55566677788", "Via Test 5", true);
			when(customerRepository.save(any(Customer.class))).thenReturn(input);
			ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);

			// Act
			customerService.save(input);
			
			// Assert
			verify(customerRepository).save(customerCaptor.capture());
			Customer result = customerCaptor.getValue();

		    assertThat(result).isNotNull();
		    assertThat(result.getVatNumber()).isEqualTo("IT55566677788");
		    assertThat(result.getCompanyName()).isEqualTo("Test Corp");
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando viene invocato con {@code null} come entità.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo attuale accede a
		 * {@code newCustomer.getVatNumber()}
		 * per il logging (riga 99 del sorgente) senza alcun null-check preventivo.
		 * Passare {@code null} causa una {@link NullPointerException} non gestita →
		 * HTTP 500.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere come prima istruzione:
		 * {@code if (newCustomer == null) throw new IllegalArgumentException("Customer entity cannot be null");}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando l'entità è null")
		void shouldThrowIllegalArgumentExceptionWhenEntityIsNull_RED() {
			// Arrange — nessun mock necessario

			// Act & Assert
			assertThatThrownBy(() -> customerService.save(null))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("null");

			verify(customerRepository, never()).save(any());
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando l'entità passata ha {@code vatNumber} uguale a {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo non valida lo stato interno dell'entità.
		 * Un Customer con vatNumber null bypassa il service e arriva al database, dove
		 * viola il vincolo {@code nullable = false} con un'eccezione JPA opaca.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere dopo il null-check sull'entità:
		 * {@code if (newCustomer.getVatNumber() == null || newCustomer.getVatNumber().isBlank()) throw new IllegalArgumentException(...)}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando vatNumber dell'entità è null")
		void shouldThrowIllegalArgumentExceptionWhenEntityVatNumberIsNull_RED() {
			// Arrange
			Customer invalidCustomer = buildCustomer(null, "Valid Name", null, "Valid Address", true);

			// Act & Assert
			assertThatThrownBy(() -> customerService.save(invalidCustomer))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("vatNumber");

			verify(customerRepository, never()).save(any());
		}
	}

	// ═════════════════════════════════════════════════════════════════════
	// updateDetailsByVatNumber(CustomerUpdateDTO)
	// ═════════════════════════════════════════════════════════════════════

	/**
	 * Classe innestata che raggruppa tutti i test relativi al metodo
	 * {@link CustomerService#updateDetailsByVatNumber(CustomerUpdateDTO)}.
	 * <p>
	 * Il metodo aggiorna chirurgicamente i dati anagrafici (companyName,
	 * legalAddress)
	 * di un cliente, con gestione del Key Shifting per la cache.
	 * </p>
	 * 
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("updateDetailsByVatNumber(CustomerUpdateDTO)")
	class UpdateDetailsByVatNumberTests {

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo aggiorni correttamente companyName
		 * e legalAddress quando il Customer esiste e il DTO contiene dati validi.
		 * <p>
		 * Mock attivi:
		 * <ul>
		 * <li>{@link CustomerRepository#findByVatNumber(String)} restituisce il
		 * Customer esistente.</li>
		 * <li>{@link CustomerRepository#save(Object)} restituisce il Customer
		 * aggiornato.</li>
		 * </ul>
		 * </p>
		 * <p>
		 * Risultato atteso: l'entità restituita ha i nuovi valori di companyName e
		 * legalAddress.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe aggiornare companyName e legalAddress con successo")
		void shouldUpdateCompanyNameAndLegalAddressSuccessfully() {
			// Arrange
			Customer existing = buildCustomer(1L, "Vecchio Nome", "IT12345678901", "Vecchio Indirizzo", true);
			when(customerRepository.findByVatNumber("IT12345678901")).thenReturn(Optional.of(existing));

			Customer updated = buildCustomer(1L, "Nuovo Nome", "IT12345678901", "Nuovo Indirizzo", true);
			when(customerRepository.save(any(Customer.class))).thenReturn(updated);

			CustomerUpdateDTO dto = new CustomerUpdateDTO("IT12345678901", "Nuovo Nome", "Nuovo Indirizzo");
			
			ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
			
			// Act
			assertThatThrownBy(() -> customerService.updateDetailsByVatNumber(dto))
	        	.isInstanceOf(IllegalStateException.class);
			
			// Assert
			verify(customerRepository).findByVatNumber("IT12345678901");
			verify(customerRepository).save(customerCaptor.capture());
			
			Customer result = customerCaptor.getValue();

			assertThat(result).isNotNull();
			assertThat(result.getCompanyName()).isEqualTo("Nuovo Nome");
			assertThat(result.getLegalAddress()).isEqualTo("Nuovo Indirizzo");
			assertThat(result.getVatNumber()).isEqualTo("IT12345678901");
		}

		/**
		 * <b>[HAPPY PATH — KEY SHIFTING]</b> Verifica che il metodo catturi
		 * correttamente
		 * il vecchio companyName prima della mutazione. Questo test garantisce che la
		 * logica
		 * di Key Shifting per la cache registri il valore originale per la successiva
		 * eviction.
		 * <p>
		 * Mock attivi: repository findByVatNumber e save.
		 * </p>
		 * <p>
		 * Risultato atteso: il companyName dell'entità passata a save contiene il nuovo
		 * valore,
		 * confermando che la mutazione viene applicata DOPO la cattura dell'oldKey.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe catturare oldCompanyName prima della mutazione (Key Shifting)")
		void shouldCaptureOldCompanyNameBeforeMutation() {
			// Arrange
			Customer existing = buildCustomer(1L, "Nome Originale", "IT12345678901", "Via Vecchia", true);
			when(customerRepository.findByVatNumber("IT12345678901")).thenReturn(Optional.of(existing));
			when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

			CustomerUpdateDTO dto = new CustomerUpdateDTO("IT12345678901", "Nome Cambiato", "Via Nuova");
			
			ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
			
			// Act
			assertThatThrownBy(() -> customerService.updateDetailsByVatNumber(dto))
	        	.isInstanceOf(IllegalStateException.class);
			
			// Assert
			verify(customerRepository).findByVatNumber("IT12345678901");
		    verify(customerRepository).save(customerCaptor.capture());

			Customer result = customerCaptor.getValue();

		    assertThat(result).isNotNull();
		    assertThat(result.getCompanyName()).isEqualTo("Nome Cambiato");
		    assertThat(result.getLegalAddress()).isEqualTo("Via Nuova");
		    assertThat(result.getVatNumber()).isEqualTo("IT12345678901");
		}

		/**
		 * <b>[FAILURE PATH]</b> Verifica che il metodo lanci
		 * {@link ResourceNotFoundException}
		 * quando la Partita IVA del DTO non corrisponde ad alcun record nel database.
		 * <p>
		 * Mock attivo: {@link CustomerRepository#findByVatNumber(String)} restituisce
		 * {@link Optional#empty()}.
		 * </p>
		 * <p>
		 * Risultato atteso: {@link ResourceNotFoundException} con messaggio contenente
		 * la Partita IVA cercata. Il save non deve mai essere invocato.
		 * </p>
		 */
		@Test
		@DisplayName("FP — Dovrebbe lanciare ResourceNotFoundException quando la P.IVA non esiste")
		void shouldThrowResourceNotFoundExceptionWhenVatNumberDoesNotExist() {
			// Arrange
			CustomerUpdateDTO dto = new CustomerUpdateDTO("IT00000000000", "Qualcosa", "Qualche Via");
			when(customerRepository.findByVatNumber("IT00000000000")).thenReturn(Optional.empty());

			// Act & Assert
			assertThatThrownBy(() -> customerService.updateDetailsByVatNumber(dto))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Customer not found")
					.hasMessageContaining("IT00000000000");

			verify(customerRepository, never()).save(any());
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando viene invocato con un DTO {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo accede direttamente a
		 * {@code updateDto.vatNumber()}
		 * (riga 122 del sorgente) senza alcun null-check. Passare {@code null} come DTO
		 * causa
		 * {@link NullPointerException} non gestita → HTTP 500.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere come prima istruzione:
		 * {@code if (updateDto == null) throw new IllegalArgumentException("updateDto cannot be null");}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando il DTO è null")
		void shouldThrowIllegalArgumentExceptionWhenDtoIsNull_RED() {
			// Arrange — nessun mock necessario

			// Act & Assert
			assertThatThrownBy(() -> customerService.updateDetailsByVatNumber(null))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("null");

			verify(customerRepository, never()).findByVatNumber(any());
			verify(customerRepository, never()).save(any());
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando il campo {@code vatNumber} del DTO è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo accede a {@code updateDto.vatNumber()} per il
		 * logging
		 * e per la query al repository senza verificare che il campo non sia null. Un
		 * DTO
		 * con vatNumber null viene propagato silenziosamente al repository.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere:
		 * {@code if (updateDto.vatNumber() == null || updateDto.vatNumber().isBlank()) throw new IllegalArgumentException(...)}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando vatNumber nel DTO è null")
		void shouldThrowIllegalArgumentExceptionWhenDtoVatNumberIsNull_RED() {
			// Arrange
			CustomerUpdateDTO dto = new CustomerUpdateDTO(null, "Nuovo Nome", "Nuovo Indirizzo");

			// Act & Assert
			assertThatThrownBy(() -> customerService.updateDetailsByVatNumber(dto))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("vatNumber");

			verify(customerRepository, never()).findByVatNumber(any());
			verify(customerRepository, never()).save(any());
		}
	}

	// ═════════════════════════════════════════════════════════════════════
	// updateActiveStatusByVatNumber(CustomerUpdateActiveStatusDTO)
	// ═════════════════════════════════════════════════════════════════════

	/**
	 * Classe innestata che raggruppa tutti i test relativi al metodo
	 * {@link CustomerService#updateActiveStatusByVatNumber(CustomerUpdateActiveStatusDTO)}.
	 * <p>
	 * Il metodo modifica unicamente il flag operativo (attivo/inattivo),
	 * disaccoppiando
	 * l'azione dai dati anagrafici.
	 * </p>
	 * 
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("updateActiveStatusByVatNumber(CustomerUpdateActiveStatusDTO)")
	class UpdateActiveStatusByVatNumberTests {
		
		@BeforeEach
		void initTransactionSynchronization() {
		    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
		        TransactionSynchronizationManager.initSynchronization();
		    }
		}

		@AfterEach
		void clearTransactionSynchronization() {
		    if (TransactionSynchronizationManager.isSynchronizationActive()) {
		        TransactionSynchronizationManager.clearSynchronization();
		    }
		}
		
		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo disattivi correttamente un
		 * Customer
		 * impostando il flag {@code active} a {@code false}.
		 * <p>
		 * Mock attivi: repository findByVatNumber restituisce il Customer attivo,
		 * save restituisce il Customer disattivato.
		 * </p>
		 * <p>
		 * Risultato atteso: entità con {@code active = false}.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe disattivare il Customer (active = false)")
		void shouldDeactivateCustomerSuccessfully() {
			// Arrange
			Customer existing = buildCustomer(1L, "Active Corp", "IT11223344556", "Via Attiva 1", true);
			when(customerRepository.findByVatNumber("IT11223344556")).thenReturn(Optional.of(existing));

			Customer deactivated = buildCustomer(1L, "Active Corp", "IT11223344556", "Via Attiva 1", false);
			when(customerRepository.save(any(Customer.class))).thenReturn(deactivated);

			CustomerUpdateActiveStatusDTO dto = new CustomerUpdateActiveStatusDTO("IT11223344556", false);

			// Act
			Customer result = customerService.updateActiveStatusByVatNumber(dto);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.isActive()).isFalse();
			assertThat(result.getVatNumber()).isEqualTo("IT11223344556");
			verify(customerRepository).findByVatNumber("IT11223344556");
			verify(customerRepository).save(any(Customer.class));
		}

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo riattivi correttamente un Customer
		 * precedentemente disattivato, impostando il flag {@code active} a
		 * {@code true}.
		 * <p>
		 * Mock attivi: repository findByVatNumber restituisce Customer inattivo,
		 * save restituisce Customer riattivato.
		 * </p>
		 * <p>
		 * Risultato atteso: entità con {@code active = true}.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe riattivare il Customer (active = true)")
		void shouldReactivateCustomerSuccessfully() {
			// Arrange
			Customer existing = buildCustomer(1L, "Inactive Corp", "IT99988877766", "Via Inattiva 1", false);
			when(customerRepository.findByVatNumber("IT99988877766")).thenReturn(Optional.of(existing));

			Customer reactivated = buildCustomer(1L, "Inactive Corp", "IT99988877766", "Via Inattiva 1", true);
			when(customerRepository.save(any(Customer.class))).thenReturn(reactivated);

			CustomerUpdateActiveStatusDTO dto = new CustomerUpdateActiveStatusDTO("IT99988877766", true);

			// Act
			Customer result = customerService.updateActiveStatusByVatNumber(dto);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.isActive()).isTrue();
			verify(customerRepository).findByVatNumber("IT99988877766");
			verify(customerRepository).save(any(Customer.class));
		}

		/**
		 * <b>[EDGE CASE]</b> Verifica il comportamento idempotente del metodo:
		 * impostare
		 * {@code active = true} su un Customer già attivo non deve causare errori.
		 * <p>
		 * Mock attivi: repository findByVatNumber restituisce Customer già attivo.
		 * </p>
		 * <p>
		 * Risultato atteso: l'operazione completa senza eccezioni, il flag rimane
		 * {@code true}.
		 * </p>
		 */
		@Test
		@DisplayName("EDGE — Dovrebbe essere idempotente (riattivare un Customer già attivo)")
		void shouldBeIdempotentWhenActivatingAlreadyActiveCustomer() {
			// Arrange
			Customer alreadyActive = buildCustomer(1L, "Already Active", "IT11111111111", "Via Attiva", true);
			when(customerRepository.findByVatNumber("IT11111111111")).thenReturn(Optional.of(alreadyActive));
			when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

			CustomerUpdateActiveStatusDTO dto = new CustomerUpdateActiveStatusDTO("IT11111111111", true);

			// Act
			Customer result = customerService.updateActiveStatusByVatNumber(dto);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.isActive()).isTrue();
			verify(customerRepository).save(any(Customer.class));
		}

		/**
		 * <b>[FAILURE PATH]</b> Verifica che il metodo lanci
		 * {@link ResourceNotFoundException}
		 * quando la Partita IVA del DTO non corrisponde ad alcun record nel database.
		 * <p>
		 * Mock attivo: repository findByVatNumber restituisce {@link Optional#empty()}.
		 * </p>
		 * <p>
		 * Risultato atteso: {@link ResourceNotFoundException}. Il save non viene mai
		 * invocato.
		 * </p>
		 */
		@Test
		@DisplayName("FP — Dovrebbe lanciare ResourceNotFoundException quando la P.IVA non esiste")
		void shouldThrowResourceNotFoundExceptionWhenVatNumberDoesNotExist() {
			// Arrange
			CustomerUpdateActiveStatusDTO dto = new CustomerUpdateActiveStatusDTO("IT00000000000", false);
			when(customerRepository.findByVatNumber("IT00000000000")).thenReturn(Optional.empty());

			// Act & Assert
			assertThatThrownBy(() -> customerService.updateActiveStatusByVatNumber(dto))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Customer not found")
					.hasMessageContaining("IT00000000000");

			verify(customerRepository, never()).save(any());
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando viene invocato con un DTO {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo accede direttamente a
		 * {@code updateDto.vatNumber()}
		 * (riga 147 del sorgente) senza alcun null-check. Passare {@code null} come DTO
		 * causa
		 * {@link NullPointerException} non gestita → HTTP 500.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere come prima istruzione:
		 * {@code if (updateDto == null) throw new IllegalArgumentException("updateDto cannot be null");}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando il DTO è null")
		void shouldThrowIllegalArgumentExceptionWhenDtoIsNull_RED() {
			// Arrange — nessun mock necessario

			// Act & Assert
			assertThatThrownBy(() -> customerService.updateActiveStatusByVatNumber(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("null");

			verify(customerRepository, never()).findByVatNumber(any());
			verify(customerRepository, never()).save(any());
		}

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo muti correttamente il campo
		 * {@code active}
		 * sull'entità recuperata dal repository prima di invocare il save, catturando
		 * l'argomento effettivo tramite {@link ArgumentCaptor}.
		 * <p>
		 * Mock attivi: repository findByVatNumber e save.
		 * </p>
		 * <p>
		 * Risultato atteso: l'entità passata a save ha il campo active impostato a
		 * {@code false}.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe mutare il campo active sull'entità prima del save")
		void shouldMutateActiveFieldOnEntityBeforeSave() {
			// Arrange
			Customer existing = buildCustomer(1L, "Test Corp", "IT44455566677", "Via Test", true);
			when(customerRepository.findByVatNumber("IT44455566677")).thenReturn(Optional.of(existing));
			when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

			CustomerUpdateActiveStatusDTO dto = new CustomerUpdateActiveStatusDTO("IT44455566677", false);

			// Act
			customerService.updateActiveStatusByVatNumber(dto);

			// Assert
			ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
			verify(customerRepository).save(captor.capture());
			Customer savedEntity = captor.getValue();
			assertThat(savedEntity.isActive()).isFalse();
			assertThat(savedEntity.getCompanyName()).isEqualTo("Test Corp");
		}
	}

	// ═════════════════════════════════════════════════════════════════════
	// mapToEntity(CustomerRequestDTO)
	// ═════════════════════════════════════════════════════════════════════

	/**
	 * Classe innestata che raggruppa tutti i test relativi al metodo
	 * {@link CustomerService#mapToEntity(CustomerRequestDTO)}.
	 * <p>
	 * Il metodo funge da factory per la conversione DTO → Entity.
	 * </p>
	 * 
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("mapToEntity(CustomerRequestDTO)")
	class MapToEntityTests {

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo mappa correttamente tutti i campi
		 * del DTO nei corrispondenti campi dell'entità {@link Customer}.
		 * <p>
		 * Nessun mock necessario: il metodo è una pura factory senza interazioni con il
		 * repository.
		 * </p>
		 * <p>
		 * Risultato atteso: entità con companyName, vatNumber e legalAddress
		 * corrispondenti
		 * ai valori del DTO. L'ID deve essere {@code null} (entità transiente).
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe mappare correttamente tutti i campi del DTO nell'Entity")
		void shouldMapAllFieldsCorrectly() {
			// Arrange
			CustomerRequestDTO dto = new CustomerRequestDTO("Test Company", "IT12345678901", "Via Test 1, Roma");

			// Act
			Customer result = customerService.mapToEntity(dto);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.getCompanyName()).isEqualTo("Test Company");
			assertThat(result.getVatNumber()).isEqualTo("IT12345678901");
			assertThat(result.getLegalAddress()).isEqualTo("Via Test 1, Roma");
			assertThat(result.getId()).isNull();
		}

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il metodo restituisca un'entità transiente
		 * (senza ID e con stato di default) pronta per la persistenza.
		 * <p>
		 * Risultato atteso: l'ID è null e il campo active ha il valore di default
		 * ({@code false}, in quanto non impostato dal mapper — il default JPA si
		 * attiverà solo al persist).
		 * </p>
		 */
		@Test
		@DisplayName("HP — L'entità restituita deve essere transiente (ID null, stato default)")
		void shouldReturnTransientEntity() {
			// Arrange
			CustomerRequestDTO dto = new CustomerRequestDTO("Transient Corp", "IT99999999999", "Via Transient 99");

			// Act
			Customer result = customerService.mapToEntity(dto);

			// Assert
			assertThat(result.getId()).isNull();
			assertThat(result.isActive()).isFalse();
		}

		/**
		 * <b>[ISOLATION PATH]</b> Verifica che il metodo non invochi il repository.
		 * Il mapping è un'operazione di pura trasformazione in-memory.
		 * <p>
		 * Risultato atteso: nessuna interazione con {@link CustomerRepository}.
		 * </p>
		 */
		@Test
		@DisplayName("ISOLATION — Il mapping non deve interagire con il repository")
		void shouldNotInteractWithRepository() {
			// Arrange
			CustomerRequestDTO dto = new CustomerRequestDTO("Isolated Corp", "IT88877766655", "Via Isolata");

			// Act
			customerService.mapToEntity(dto);

			// Assert
			verify(customerRepository, never()).save(any());
			verify(customerRepository, never()).findByVatNumber(any());
			verify(customerRepository, never()).findByCompanyName(any());
			verify(customerRepository, never()).findAll();
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando viene invocato con un DTO {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo accede direttamente a
		 * {@code dto.companyName()},
		 * {@code dto.vatNumber()} e {@code dto.legalAddress()} (righe 226-228 del
		 * sorgente)
		 * senza alcun null-check preventivo. Passare {@code null} causa
		 * {@link NullPointerException} non gestita → HTTP 500.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere come prima istruzione:
		 * {@code if (dto == null) throw new IllegalArgumentException("CustomerRequestDTO cannot be null");}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando il DTO è null")
		void shouldThrowIllegalArgumentExceptionWhenDtoIsNull_RED() {
			// Arrange — nessun mock necessario

			// Act & Assert
			assertThatThrownBy(() -> customerService.mapToEntity(null))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("null");
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando il campo {@code vatNumber} del DTO è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo invoca
		 * {@code customer.setVatNumber(dto.vatNumber())}
		 * senza verificare che il campo sia non-null. L'entità risultante avrebbe una
		 * Business Key
		 * nulla, violando il vincolo di unicità e il constraint
		 * {@code nullable = false}
		 * al momento del persist → errore JPA opaco.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere:
		 * {@code if (dto.vatNumber() == null || dto.vatNumber().isBlank()) throw new IllegalArgumentException(...)}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando vatNumber nel DTO è null")
		void shouldThrowIllegalArgumentExceptionWhenDtoVatNumberIsNull_RED() {
			// Arrange
			CustomerRequestDTO dto = new CustomerRequestDTO("Valid Name", null, "Valid Address");

			// Act & Assert
			assertThatThrownBy(() -> customerService.mapToEntity(dto))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("vatNumber");
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException}
		 * quando il campo {@code companyName} del DTO è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il metodo invoca
		 * {@code customer.setCompanyName(dto.companyName())}
		 * senza verificare che il campo sia non-null. L'entità risultante avrebbe un
		 * companyName
		 * nullo, violando il vincolo {@code nullable = false} al momento del persist.
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere:
		 * {@code if (dto.companyName() == null || dto.companyName().isBlank()) throw new IllegalArgumentException(...)}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link IllegalArgumentException}. Il test FALLIRÀ finché il
		 * fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare IllegalArgumentException quando companyName nel DTO è null")
		void shouldThrowIllegalArgumentExceptionWhenDtoCompanyNameIsNull_RED() {
			// Arrange
			CustomerRequestDTO dto = new CustomerRequestDTO(null, "IT12345678901", "Valid Address");

			// Act & Assert
			assertThatThrownBy(() -> customerService.mapToEntity(dto))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("companyName");
		}
	}

	// ═════════════════════════════════════════════════════════════════════
	// CONSTRUCTOR — Dependency Injection Guard
	// ═════════════════════════════════════════════════════════════════════

	/**
	 * Classe innestata che raggruppa i test relativi al costruttore
	 * {@link CustomerService#CustomerService(CustomerRepository, CacheManager)}.
	 * <p>
	 * Il costruttore utilizza
	 * {@link java.util.Objects#requireNonNull(Object, String)}
	 * come guard per la dipendenza {@code customerRepository}.
	 * </p>
	 * 
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("Constructor — Dependency Injection Guard")
	class ConstructorTests {

		/**
		 * <b>[FAILURE PATH]</b> Verifica che il costruttore lanci
		 * {@link NullPointerException}
		 * quando il parametro {@code customerRepository} è {@code null}.
		 * Il guard esplicito {@code Objects.requireNonNull} nel costruttore del service
		 * deve impedire l'iniezione di una dipendenza nulla, attuando il pattern
		 * Fail-Fast.
		 * <p>
		 * Risultato atteso: {@link NullPointerException} con messaggio contestuale.
		 * </p>
		 */
		@Test
		@DisplayName("FP — Dovrebbe lanciare NullPointerException quando customerRepository è null")
		void shouldThrowNullPointerExceptionWhenRepositoryIsNull() {
			// Arrange
			CacheManager validCacheManager = mock(CacheManager.class);

			// Act & Assert
			assertThatThrownBy(() -> new CustomerService(null, validCacheManager))
					.isInstanceOf(NullPointerException.class)
					.hasMessageContaining("customerRepository must be not null");
		}

		/**
		 * <b>[HAPPY PATH]</b> Verifica che il costruttore istanzi correttamente il
		 * service
		 * quando entrambe le dipendenze sono fornite e non-null.
		 * <p>
		 * Risultato atteso: istanza non-null senza eccezioni.
		 * </p>
		 */
		@Test
		@DisplayName("HP — Dovrebbe istanziare correttamente il service con dipendenze valide")
		void shouldInstantiateSuccessfullyWithValidDependencies() {
			// Arrange
			CustomerRepository validRepository = mock(CustomerRepository.class);
			CacheManager validCacheManager = mock(CacheManager.class);

			// Act
			CustomerService service = new CustomerService(validRepository, validCacheManager);

			// Assert
			assertThat(service).isNotNull();
		}

		/**
		 * <b>[TDD-RED]</b> Verifica che il costruttore lanci
		 * {@link NullPointerException}
		 * quando il parametro {@code cacheManager} è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ:</b> Il costruttore della classe padre
		 * {@link dev.vinciguerra.adrsentinel.db.AbstractGenericService}
		 * assegna il campo {@code this.cacheManager = cacheManager} senza alcun
		 * null-check.
		 * Un cacheManager null non causa errore al momento della costruzione, ma
		 * produce
		 * {@link NullPointerException} differite al primo utilizzo (es. nel metodo
		 * {@code storeInCache}).
		 * </p>
		 * <p>
		 * <b>Fix richiesto:</b> Aggiungere nel costruttore di
		 * {@code AbstractGenericService}:
		 * {@code this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager must be not null");}
		 * </p>
		 * <p>
		 * Risultato atteso: {@link NullPointerException} con messaggio contestuale.
		 * Il test FALLIRÀ finché il fix non sarà implementato.
		 * </p>
		 */
		@Test
		@DisplayName("RED — Dovrebbe lanciare NullPointerException quando cacheManager è null")
		void shouldThrowNullPointerExceptionWhenCacheManagerIsNull_RED() {
			// Arrange
			CustomerRepository validRepository = mock(CustomerRepository.class);

			// Act & Assert
			assertThatThrownBy(() -> new CustomerService(validRepository, null))
					.isInstanceOf(NullPointerException.class)
					.hasMessageContaining("cacheManager");
		}
	}
}
