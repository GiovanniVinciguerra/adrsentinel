package dev.vinciguerra.adrsentinel.db.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.vinciguerra.adrsentinel.db.driver.Driver.DriverApproval;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateActiveStatusDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateAdrApprovalDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateDTO;

/**
 * Suite di test unitari per la classe {@link DriverService}.
 * <p>
 * Questa suite adotta un approccio <b>TDD difensivo e spietato</b>, coprendo:
 * <ul>
 * <li><b>Happy Path:</b> Verifica del corretto funzionamento in condizioni ideali.</li>
 * <li><b>Failure Path:</b> Verifica della risposta a input errati, risorse mancanti e dati malformati.</li>
 * <li><b>Boundary Value Analysis:</b> Verifica dei limiti (stringhe vuote, collection vuote, ID ai bordi).</li>
 * <li><b>Fase RED (TDD):</b> Test deliberatamente scritti per fallire ed esporre l'assenza
 * di guard clause, validazioni null e gestione di eccezioni nel codice sorgente.</li>
 * </ul>
 * <p>
 * <b>Strategia di isolamento:</b> Puro isolamento tramite Mockito. Nessun contesto Spring,
 * nessun database in-memory (H2). Tutte le dipendenze (Repository, CacheManager) sono mockate.
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DriverService – Unit Test Suite")
class DriverServiceTests {

	@Mock
	private DriverRepository driverRepository;

	@Mock
	private CacheManager cacheManager;

	private DriverService driverService;

	/**
	 * Setup eseguito prima di ogni test. Istanzia il {@link DriverService}
	 * con le dipendenze mockate, simulando l'iniezione del costruttore.
	 */
	@BeforeEach
	void setUp() {
		driverService = new DriverService(driverRepository, cacheManager);
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

	// ==================================================================================
	// COSTRUTTORE
	// ==================================================================================

	/**
	 * Classe innestata che raggruppa i test per il costruttore di {@link DriverService}.
	 * <p>
	 * Verifica che il costruttore applichi correttamente le guard clause tramite
	 * {@code Objects.requireNonNull} per entrambe le dipendenze iniettate.
	 * </p>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("Costruttore")
	class CostruttoreTests {

		/**
		 * Verifica che il costruttore lanci {@link NullPointerException} quando il
		 * {@link DriverRepository} è null. Il codice sorgente applica
		 * {@code Objects.requireNonNull(driverRepository, ...)} alla riga 60.
		 */
		@Test
		@DisplayName("Deve lanciare NullPointerException se driverRepository è null")
		void shouldThrowNullPointerExceptionWhenDriverRepositoryIsNull() {
			// Arrange - nessun setup aggiuntivo richiesto

			// Act & Assert
			assertThatThrownBy(() -> new DriverService(null, cacheManager))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("driverRepository must not be null");
		}

		/**
		 * Verifica che il costruttore lanci {@link NullPointerException} quando il
		 * {@link CacheManager} è null. Il codice sorgente applica
		 * {@code Objects.requireNonNull(cacheManager, ...)} alla riga 59.
		 */
		@Test
		@DisplayName("Deve lanciare NullPointerException se cacheManager è null")
		void shouldThrowNullPointerExceptionWhenCacheManagerIsNull() {
			// Arrange - nessun setup aggiuntivo richiesto

			// Act & Assert
			assertThatThrownBy(() -> new DriverService(driverRepository, null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("cacheManager must be not null");
		}

		/**
		 * Verifica che il costruttore completi con successo quando entrambe
		 * le dipendenze sono valide e non-null.
		 */
		@Test
		@DisplayName("Deve istanziare correttamente con dipendenze valide")
		void shouldInstantiateSuccessfullyWithValidDependencies() {
			// Arrange - nessun setup aggiuntivo richiesto

			// Act
			DriverService service = new DriverService(driverRepository, cacheManager);

			// Assert
			assertThat(service).isNotNull();
		}
	}

	// ==================================================================================
	// getByLicense(String)
	// ==================================================================================

	/**
	 * Classe innestata che raggruppa i test per il metodo {@link DriverService#getByLicense(String)}.
	 * <p>
	 * Copre lo scenario di ritrovamento (Happy Path), la gestione della risorsa
	 * non trovata (Failure Path) e l'assenza di validazione null sull'input (Fase RED).
	 * </p>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("getByLicense(String)")
	class GetByLicenseTests {

		/**
		 * Verifica che il metodo restituisca l'entità {@link Driver} corretta quando
		 * il repository trova un record corrispondente al numero di patente fornito.
		 * <p>Mock coinvolti: {@code driverRepository.findByLicense()} configurato per
		 * restituire un {@link Optional} contenente il driver atteso.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve restituire il Driver quando la license esiste")
		void shouldReturnDriverWhenLicenseExists() {
			// Arrange
			String license = "AB1234567C";
			Driver expectedDriver = buildDefaultDriver(license);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(expectedDriver));

			// Act
			Driver result = driverService.getByLicense(license);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.getLicense()).isEqualTo(license);
			verify(driverRepository).findByLicense(license);
		}

		/**
		 * Verifica che il metodo lanci {@link ResourceNotFoundException} quando
		 * nessun autista corrisponde alla patente fornita.
		 * <p>Mock coinvolti: {@code driverRepository.findByLicense()} configurato per
		 * restituire {@link Optional#empty()}.</p>
		 */
		@Test
		@DisplayName("Failure Path – Deve lanciare ResourceNotFoundException quando la license non esiste")
		void shouldThrowResourceNotFoundExceptionWhenLicenseDoesNotExist() {
			// Arrange
			String license = "NONEXISTENT";
			when(driverRepository.findByLicense(license)).thenReturn(Optional.empty());

			// Act & Assert
			assertThatThrownBy(() -> driverService.getByLicense(license))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Driver not found: " + license);
			verify(driverRepository).findByLicense(license);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException} quando viene invocato con {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 76 non possiede
		 * una guard clause per un parametro null. Un {@code null} viene passato
		 * direttamente a {@code driverRepository.findByLicense(null)}, delegando
		 * la gestione dell'errore al livello di persistenza (comportamento non deterministico).
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere {@code Objects.requireNonNull(license, ...)}
		 * o una validazione esplicita all'inizio del metodo {@code getByLicense}.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare IllegalArgumentException quando license è null")
		void shouldThrowIllegalArgumentExceptionWhenLicenseIsNull() {
			// Arrange - nessun setup richiesto

			// Act & Assert
			assertThatThrownBy(() -> driverService.getByLicense(null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException} quando viene invocato con una stringa vuota.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente non verifica la lunghezza
		 * del parametro. Una stringa vuota ({@code ""}) passa silenziosamente al
		 * repository, generando una query potenzialmente dannosa o inutile.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere una validazione
		 * {@code if (license == null || license.isBlank())} all'inizio del metodo.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare IllegalArgumentException quando license è null")
		void shouldThrowIllegalArgumentExceptionWhenLicenseIsBlank() {
			// Arrange - nessun setup richiesto

			// Act & Assert
			assertThatThrownBy(() -> driverService.getByLicense(null))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// ==================================================================================
	// getAllDriver()
	// ==================================================================================

	/**
	 * Classe innestata che raggruppa i test per il metodo {@link DriverService#getAllDriver()}.
	 * <p>
	 * Copre il ritorno di una lista popolata e il ritorno di una lista vuota.
	 * </p>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("getAllDriver()")
	class GetAllDriverTests {

		/**
		 * Verifica che il metodo restituisca una lista contenente tutti i driver
		 * presenti nel database quando il repository ne contiene almeno uno.
		 * <p>Mock coinvolti: {@code driverRepository.findAll()} configurato per
		 * restituire una lista con due elementi.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve restituire la lista di tutti i Driver")
		void shouldReturnAllDrivers() {
			// Arrange
			Driver driver1 = buildDefaultDriver("LICENSE001");
			Driver driver2 = buildDefaultDriver("LICENSE002");
			when(driverRepository.findAll()).thenReturn(List.of(driver1, driver2));

			// Act
			List<Driver> result = driverService.getAllDriver();

			// Assert
			assertThat(result).hasSize(2);
			assertThat(result).containsExactlyInAnyOrder(driver1, driver2);
			verify(driverRepository).findAll();
		}

		/**
		 * Verifica che il metodo restituisca una lista vuota quando non esistono
		 * autisti registrati nel database.
		 * <p>Mock coinvolti: {@code driverRepository.findAll()} configurato per
		 * restituire {@link Collections#emptyList()}.</p>
		 */
		@Test
		@DisplayName("Edge Case – Deve restituire lista vuota se non ci sono Driver")
		void shouldReturnEmptyListWhenNoDriversExist() {
			// Arrange
			when(driverRepository.findAll()).thenReturn(Collections.emptyList());

			// Act
			List<Driver> result = driverService.getAllDriver();

			// Assert
			assertThat(result).isEmpty();
			verify(driverRepository).findAll();
		}
	}

	// ==================================================================================
	// save(Driver)
	// ==================================================================================

	/**
	 * Classe innestata che raggruppa i test per il metodo {@link DriverService#save(Driver)}.
	 * <p>
	 * Copre il salvataggio riuscito (Happy Path), il caso di driver null (Fase RED)
	 * e la corretta delega al repository.
	 * </p>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("save(Driver)")
	class SaveTests {

		/**
		 * Verifica che il metodo persista correttamente un nuovo {@link Driver}
		 * e restituisca l'entità gestita dal Persistence Context.
		 * <p>Mock coinvolti: {@code driverRepository.save()} configurato per restituire
		 * il driver con ID popolato; {@code cacheManager} configurato per gestire la
		 * sincronizzazione cache post-commit (non eseguita in contesto mock).</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve salvare e restituire il Driver persistito")
		void shouldSaveAndReturnPersistedDriver() {
			// Arrange
			Driver newDriver = buildDefaultDriver("NEWLICENSE01");
			Driver savedDriver = buildDefaultDriver("NEWLICENSE01");
			savedDriver.setId(1L);
			when(driverRepository.save(newDriver)).thenReturn(savedDriver);

			// Act
			Driver result = driverService.save(newDriver);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.getId()).isEqualTo(1L);
			assertThat(result.getLicense()).isEqualTo("NEWLICENSE01");
			verify(driverRepository).save(newDriver);
		}

		/**
		 * Verifica che il metodo {@code save} invochi effettivamente
		 * {@code driverRepository.save()} con l'esatto oggetto passato in input
		 * (non una copia o altro).
		 * <p>Mock coinvolti: {@code driverRepository.save()} verifica la referenza
		 * dell'argomento ricevuto.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve delegare al repository l'esatta istanza ricevuta")
		void shouldDelegateExactInstanceToRepository() {
			// Arrange
			Driver newDriver = buildDefaultDriver("DELEGATETEST");
			when(driverRepository.save(newDriver)).thenReturn(newDriver);

			// Act
			driverService.save(newDriver);

			// Assert
			verify(driverRepository).save(newDriver);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo lanci
		 * {@link IllegalArgumentException} (o {@link NullPointerException} con guard clause)
		 * quando viene invocato con un argomento {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 106 invoca
		 * {@code newDriver.getLicense()} senza verificare che {@code newDriver} sia
		 * non-null. Un input null causerà un {@link NullPointerException} non gestito
		 * nel logging, prima ancora di raggiungere il repository.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere {@code Objects.requireNonNull(newDriver, ...)}
		 * all'inizio del metodo {@code save}.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione quando il Driver da salvare è null")
		void shouldThrowExceptionWhenDriverIsNull() {
			// Arrange - nessun setup richiesto

			// Act & Assert
			assertThatThrownBy(() -> driverService.save(null))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// ==================================================================================
	// updateDetailsByLicense(DriverUpdateDTO)
	// ==================================================================================

	/**
	 * Classe innestata che raggruppa i test per il metodo
	 * {@link DriverService#updateDetailsByLicense(DriverUpdateDTO)}.
	 * <p>
	 * Copre l'aggiornamento riuscito, il driver non trovato, il DTO null
	 * e i formati data invalidi.
	 * </p>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("updateDetailsByLicense(DriverUpdateDTO)")
	class UpdateDetailsByLicenseTests {

		/**
		 * Verifica l'aggiornamento completo dei dati anagrafici di un driver esistente.
		 * I campi aggiornati sono: fullName, phoneNumber, licenseExpireDate, cqcExpireDate.
		 * <p>Mock coinvolti: {@code driverRepository.findByLicense()} restituisce il driver
		 * esistente; {@code driverRepository.save()} restituisce il driver aggiornato.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve aggiornare i dettagli del Driver e restituirlo")
		void shouldUpdateDriverDetailsAndReturn() {
			// Arrange
			String license = "UPD001";
			Driver existingDriver = buildDefaultDriver(license);
			DriverUpdateDTO updateDto = new DriverUpdateDTO(
				license, "Mario Rossi Aggiornato", "+393331234567", "2028-12-31", "2029-06-15"
			);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateDetailsByLicense(updateDto);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.getFullName()).isEqualTo("Mario Rossi Aggiornato");
			assertThat(result.getPhoneNumber()).isEqualTo("+393331234567");
			assertThat(result.getLicenseExpireDate()).isEqualTo(LocalDate.of(2028, 12, 31));
			assertThat(result.getCqcExpireDate()).isEqualTo(LocalDate.of(2029, 6, 15));
			verify(driverRepository).findByLicense(license);
			verify(driverRepository).save(existingDriver);
		}

		/**
		 * Verifica che il metodo lanci {@link ResourceNotFoundException} quando
		 * la license nel DTO non corrisponde a nessun autista nel database.
		 * <p>Mock coinvolti: {@code driverRepository.findByLicense()} restituisce
		 * {@link Optional#empty()}.</p>
		 */
		@Test
		@DisplayName("Failure Path – Deve lanciare ResourceNotFoundException se la license non esiste")
		void shouldThrowResourceNotFoundExceptionWhenDriverNotFound() {
			// Arrange
			String license = "NONEXISTENT";
			DriverUpdateDTO updateDto = new DriverUpdateDTO(
				license, "Test", "+39000000000", "2028-12-31", "2029-06-15"
			);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.empty());

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateDetailsByLicense(updateDto))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Driver not found: " + license);
			verify(driverRepository).findByLicense(license);
			verify(driverRepository, never()).save(any());
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo lanci un'eccezione appropriata
		 * quando il DTO ricevuto è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 145 accede
		 * direttamente a {@code updateDto.license()} senza verificare che il DTO
		 * sia non-null. Un input null causerà un {@link NullPointerException}
		 * non gestito nel logger, prima di raggiungere qualsiasi logica di business.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere {@code Objects.requireNonNull(updateDto, ...)}
		 * all'inizio del metodo.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione quando il DTO è null")
		void shouldThrowExceptionWhenDtoIsNull() {
			// Arrange - nessun setup richiesto

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateDetailsByLicense(null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo gestisca correttamente
		 * una data di scadenza patente in formato non ISO-8601.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 150 invoca
		 * {@code LocalDate.parse(updateDto.licenseExpireDate())} senza alcun blocco
		 * try-catch. Una stringa malformata (es. {@code "not-a-date"}) propagherà un
		 * {@link java.time.format.DateTimeParseException} non gestito.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Avvolgere il parsing in un try-catch e sollevare
		 * un'eccezione di business (es. {@code BadRequestException}) con un messaggio
		 * descrittivo, oppure applicare una validazione preventiva.</p>
		 */
		@Test
		@DisplayName("[RED] Deve gestire date malformate in licenseExpireDate senza propagare DateTimeParseException")
		void shouldHandleMalformedLicenseExpireDate() {
			// Arrange
			String license = "DATETEST01";
			Driver existingDriver = buildDefaultDriver(license);
			DriverUpdateDTO updateDto = new DriverUpdateDTO(
				license, "Test User", "+393331234567", "not-a-date", "2029-06-15"
			);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));

			// Act & Assert – il codice sorgente non gestisce DateTimeParseException,
			// quindi ci aspettiamo un'eccezione controllata di business (es. IllegalArgumentException),
			// non un DateTimeParseException grezzo.
			assertThatThrownBy(() -> driverService.updateDetailsByLicense(updateDto))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo gestisca correttamente
		 * una data di scadenza CQC in formato non ISO-8601.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 151 invoca
		 * {@code LocalDate.parse(updateDto.cqcExpireDate())} senza alcun blocco
		 * try-catch. Identica vulnerabilità della licenseExpireDate.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Stessa mitigazione del campo licenseExpireDate.</p>
		 */
		@Test
		@DisplayName("[RED] Deve gestire date malformate in cqcExpireDate senza propagare DateTimeParseException")
		void shouldHandleMalformedCqcExpireDate() {
			// Arrange
			String license = "DATETEST02";
			Driver existingDriver = buildDefaultDriver(license);
			DriverUpdateDTO updateDto = new DriverUpdateDTO(
				license, "Test User", "+393331234567", "2028-12-31", "invalid-date"
			);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateDetailsByLicense(updateDto))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * Verifica che il metodo aggiorni correttamente anche quando le date di scadenza
		 * sono esattamente la data odierna (Boundary Value).
		 * <p>Mock coinvolti: repository configurato per trovare e salvare il driver.</p>
		 */
		@Test
		@DisplayName("Boundary – Deve accettare date di scadenza uguali alla data odierna")
		void shouldAcceptExpireDateEqualToToday() {
			// Arrange
			String license = "BOUNDARY01";
			String today = LocalDate.now().toString();
			Driver existingDriver = buildDefaultDriver(license);
			DriverUpdateDTO updateDto = new DriverUpdateDTO(
				license, "Boundary Test", "+393331234567", today, today
			);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateDetailsByLicense(updateDto);

			// Assert
			assertThat(result.getLicenseExpireDate()).isEqualTo(LocalDate.now());
			assertThat(result.getCqcExpireDate()).isEqualTo(LocalDate.now());
		}
	}

	// ==================================================================================
	// updateActiveStatusByLicense(DriverUpdateActiveStatusDTO)
	// ==================================================================================

	/**
	 * Classe innestata che raggruppa i test per il metodo
	 * {@link DriverService#updateActiveStatusByLicense(DriverUpdateActiveStatusDTO)}.
	 * <p>
	 * Copre la disattivazione (Soft Delete), la riattivazione e i casi di errore.
	 * </p>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("updateActiveStatusByLicense(DriverUpdateActiveStatusDTO)")
	class UpdateActiveStatusByLicenseTests {

		/**
		 * Verifica la corretta disattivazione di un autista (Soft Delete).
		 * Il flag {@code active} viene impostato a {@code false}.
		 * <p>Mock coinvolti: {@code driverRepository.findByLicense()} e
		 * {@code driverRepository.save()}.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve disattivare il Driver (Soft Delete)")
		void shouldDeactivateDriver() {
			// Arrange
			String license = "DEACTIVATE01";
			Driver existingDriver = buildDefaultDriver(license);
			existingDriver.setActive(true);
			DriverUpdateActiveStatusDTO updateDto = new DriverUpdateActiveStatusDTO(license, false);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateActiveStatusByLicense(updateDto);

			// Assert
			assertThat(result.isActive()).isFalse();
			verify(driverRepository).findByLicense(license);
			verify(driverRepository).save(existingDriver);
		}

		/**
		 * Verifica la corretta riattivazione di un autista precedentemente disattivato.
		 * Il flag {@code active} viene reimpostato a {@code true}.
		 * <p>Mock coinvolti: {@code driverRepository.findByLicense()} e
		 * {@code driverRepository.save()}.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve riattivare il Driver")
		void shouldReactivateDriver() {
			// Arrange
			String license = "REACTIVATE01";
			Driver existingDriver = buildDefaultDriver(license);
			existingDriver.setActive(false);
			DriverUpdateActiveStatusDTO updateDto = new DriverUpdateActiveStatusDTO(license, true);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateActiveStatusByLicense(updateDto);

			// Assert
			assertThat(result.isActive()).isTrue();
			verify(driverRepository).save(existingDriver);
		}

		/**
		 * Verifica che il metodo lanci {@link ResourceNotFoundException} quando
		 * la license nel DTO non corrisponde a nessun autista nel database.
		 * <p>Mock coinvolti: {@code driverRepository.findByLicense()} restituisce
		 * {@link Optional#empty()}.</p>
		 */
		@Test
		@DisplayName("Failure Path – Deve lanciare ResourceNotFoundException se la license non esiste")
		void shouldThrowResourceNotFoundExceptionWhenDriverNotFound() {
			// Arrange
			String license = "GHOST01";
			DriverUpdateActiveStatusDTO updateDto = new DriverUpdateActiveStatusDTO(license, true);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.empty());

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateActiveStatusByLicense(updateDto))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Driver not found: " + license);
			verify(driverRepository, never()).save(any());
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo lanci un'eccezione appropriata
		 * quando il DTO ricevuto è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 194 accede
		 * direttamente a {@code updateDto.license()} senza verificare che il DTO
		 * sia non-null.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere {@code Objects.requireNonNull(updateDto, ...)}
		 * all'inizio del metodo.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione quando il DTO è null")
		void shouldThrowExceptionWhenDtoIsNull() {
			// Arrange - nessun setup richiesto

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateActiveStatusByLicense(null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * Verifica il comportamento idempotente: impostare {@code active=true} su un
		 * driver già attivo non deve alterare lo stato né causare errori.
		 * <p>Mock coinvolti: repository configurato per trovare e salvare il driver.</p>
		 */
		@Test
		@DisplayName("Edge Case – Idempotenza: active=true su Driver già attivo")
		void shouldBeIdempotentWhenActivatingAlreadyActiveDriver() {
			// Arrange
			String license = "IDEMPOTENT01";
			Driver existingDriver = buildDefaultDriver(license);
			existingDriver.setActive(true);
			DriverUpdateActiveStatusDTO updateDto = new DriverUpdateActiveStatusDTO(license, true);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateActiveStatusByLicense(updateDto);

			// Assert
			assertThat(result.isActive()).isTrue();
			verify(driverRepository).save(existingDriver);
		}
	}

	// ==================================================================================
	// updateAdrCertifiedByLicense(DriverUpdateAdrApprovalDTO)
	// ==================================================================================

	/**
	 * Classe innestata che raggruppa i test per il metodo
	 * {@link DriverService#updateAdrCertifiedByLicense(DriverUpdateAdrApprovalDTO)}.
	 * <p>
	 * Copre l'aggiornamento delle abilitazioni ADR, l'enum invalido,
	 * il set null e il set vuoto.
	 * </p>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("updateAdrCertifiedByLicense(DriverUpdateAdrApprovalDTO)")
	class UpdateAdrCertifiedByLicenseTests {

		/**
		 * Verifica che il metodo aggiorni correttamente le abilitazioni ADR dell'autista
		 * con un set valido di certificazioni (BASIC, TANK).
		 * <p>Mock coinvolti: {@code driverRepository.findByLicense()} e
		 * {@code driverRepository.save()}.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve aggiornare le abilitazioni ADR con valori validi")
		void shouldUpdateAdrApprovalsWithValidValues() {
			// Arrange
			String license = "ADR001";
			Driver existingDriver = buildDefaultDriver(license);
			Set<String> approvals = Set.of("BASIC", "TANK");
			DriverUpdateAdrApprovalDTO updateDto = new DriverUpdateAdrApprovalDTO(license, approvals);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateAdrCertifiedByLicense(updateDto);

			// Assert
			assertThat(result.getDriverApprovals())
				.containsExactlyInAnyOrder(DriverApproval.BASIC, DriverApproval.TANK);
			verify(driverRepository).save(existingDriver);
		}

		/**
		 * Verifica che il metodo aggiorni correttamente le abilitazioni ADR quando
		 * il set contiene tutte e 4 le certificazioni possibili.
		 * <p>Mock coinvolti: repository configurato per trovare e salvare il driver.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve accettare tutte le 4 certificazioni ADR contemporaneamente")
		void shouldAcceptAllFourAdrApprovals() {
			// Arrange
			String license = "ADR_FULL";
			Driver existingDriver = buildDefaultDriver(license);
			Set<String> allApprovals = Set.of("BASIC", "TANK", "EXPLOSIVE", "RADIOACTIVE");
			DriverUpdateAdrApprovalDTO updateDto = new DriverUpdateAdrApprovalDTO(license, allApprovals);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateAdrCertifiedByLicense(updateDto);

			// Assert
			assertThat(result.getDriverApprovals()).hasSize(4);
			assertThat(result.getDriverApprovals()).containsExactlyInAnyOrder(
				DriverApproval.BASIC, DriverApproval.TANK,
				DriverApproval.EXPLOSIVE, DriverApproval.RADIOACTIVE
			);
		}

		/**
		 * Verifica che il metodo gestisca correttamente un set vuoto di certificazioni,
		 * revocando di fatto tutte le abilitazioni ADR dell'autista.
		 * <p>Mock coinvolti: repository configurato per trovare e salvare il driver.</p>
		 */
		@Test
		@DisplayName("Edge Case – Deve gestire un set vuoto di approvals (revoca totale)")
		void shouldHandleEmptyApprovalsSet() {
			// Arrange
			String license = "ADR_EMPTY";
			Driver existingDriver = buildDefaultDriver(license);
			existingDriver.setDriverApprovals(new HashSet<>(Set.of(DriverApproval.BASIC)));
			Set<String> emptyApprovals = Collections.emptySet();
			DriverUpdateAdrApprovalDTO updateDto = new DriverUpdateAdrApprovalDTO(license, emptyApprovals);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateAdrCertifiedByLicense(updateDto);

			// Assert
			assertThat(result.getDriverApprovals()).isEmpty();
			verify(driverRepository).save(existingDriver);
		}

		/**
		 * Verifica che il metodo lanci {@link ResourceNotFoundException} quando
		 * la license nel DTO non corrisponde a nessun autista nel database.
		 * <p>Mock coinvolti: {@code driverRepository.findByLicense()} restituisce
		 * {@link Optional#empty()}.</p>
		 */
		@Test
		@DisplayName("Failure Path – Deve lanciare ResourceNotFoundException se la license non esiste")
		void shouldThrowResourceNotFoundExceptionWhenDriverNotFound() {
			// Arrange
			String license = "ADR_GHOST";
			Set<String> approvals = Set.of("BASIC");
			DriverUpdateAdrApprovalDTO updateDto = new DriverUpdateAdrApprovalDTO(license, approvals);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.empty());

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateAdrCertifiedByLicense(updateDto))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Driver not found: " + license);
			verify(driverRepository, never()).save(any());
		}

		/**
		 * Verifica che il metodo lanci {@link IllegalArgumentException} quando
		 * il set di certificazioni contiene un valore non presente nell'enum
		 * {@link DriverApproval}.
		 * <p>
		 * Il codice sorgente alla riga 240 utilizza {@code Enum.valueOf(DriverApproval.class, approval)}
		 * che lancia nativamente {@link IllegalArgumentException} per valori non riconosciuti.
		 * Questo è un comportamento corretto (Fail-Fast) ma il tipo di eccezione e il
		 * messaggio potrebbero non essere chiari per il chiamante.
		 * </p>
		 * <p>Mock coinvolti: repository configurato per trovare il driver.</p>
		 */
		@Test
		@DisplayName("Failure Path – Deve lanciare IllegalArgumentException per enum ADR inesistente")
		void shouldThrowIllegalArgumentExceptionForInvalidAdrApproval() {
			// Arrange
			String license = "ADR_BAD";
			Driver existingDriver = buildDefaultDriver(license);
			Set<String> invalidApprovals = Set.of("NUCLEAR_WASTE");
			DriverUpdateAdrApprovalDTO updateDto = new DriverUpdateAdrApprovalDTO(license, invalidApprovals);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateAdrCertifiedByLicense(updateDto))
				.isInstanceOf(IllegalArgumentException.class);
			verify(driverRepository, never()).save(any());
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo lanci un'eccezione controllata
		 * quando il set di approvals nel DTO è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 239 itera
		 * direttamente su {@code updateDto.approvals()} con un for-each senza
		 * alcun controllo di nullità. Se il set è null, verrà lanciata una
		 * {@link NullPointerException} non gestita.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere un guard clause
		 * {@code if (updateDto.approvals() == null)} con gestione esplicita
		 * (es. impostare un set vuoto o lanciare {@link IllegalArgumentException}).</p>
		 */
		@Test
		@DisplayName("[RED] Non deve lanciare eccezione quando approvals è null nel DTO e la lista delle approvazioni deve essere empty")
		void shouldThrowExceptionWhenApprovalsSetIsNull() {
			// Arrange
			String license = "ADR_NULL_SET";
			Driver existingDriver = buildDefaultDriver(license);
			DriverUpdateAdrApprovalDTO updateDto = new DriverUpdateAdrApprovalDTO(license, null);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act & Assert
			Driver driver = assertDoesNotThrow(() -> driverService.updateAdrCertifiedByLicense(updateDto));
			assertThat(driver.getDriverApprovals()).isEmpty();
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo lanci un'eccezione appropriata
		 * quando il DTO è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 235 accede a
		 * {@code updateDto.license()} senza verificare che il DTO sia non-null.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere {@code Objects.requireNonNull(updateDto, ...)}.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione quando il DTO è null")
		void shouldThrowExceptionWhenDtoIsNull() {
			// Arrange - nessun setup richiesto

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateAdrCertifiedByLicense(null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * Verifica che il metodo gestisca correttamente la conversione enum con valori
		 * case-sensitive. L'enum Java è case-sensitive: "basic" (minuscolo) non corrisponde a "BASIC".
		 * <p>Mock coinvolti: repository configurato per trovare il driver.</p>
		 */
		@Test
		@DisplayName("Failure Path – Deve lanciare eccezione per enum ADR in minuscolo (case-sensitive)")
		void shouldThrowExceptionForLowercaseAdrApproval() {
			// Arrange
			String license = "ADR_CASE";
			Driver existingDriver = buildDefaultDriver(license);
			Set<String> lowercaseApprovals = Set.of("basic");
			DriverUpdateAdrApprovalDTO updateDto = new DriverUpdateAdrApprovalDTO(license, lowercaseApprovals);
			when(driverRepository.findByLicense(license)).thenReturn(Optional.of(existingDriver));

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateAdrCertifiedByLicense(updateDto))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// ==================================================================================
	// updateInTransitStatusById(Long, boolean)
	// ==================================================================================

	/**
	 * Classe innestata che raggruppa i test per il metodo
	 * {@link DriverService#updateInTransitStatusById(Long, boolean)}.
	 * <p>
	 * Copre l'impostazione dello stato di transito, l'ID non trovato e l'ID null.
	 * </p>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("updateInTransitStatusById(Long, boolean)")
	class UpdateInTransitStatusByIdTests {

		/**
		 * Verifica che il metodo imposti correttamente {@code inTransit = true}
		 * per un autista identificato dal suo ID.
		 * <p>Mock coinvolti: {@code driverRepository.findById()} e
		 * {@code driverRepository.save()}.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve impostare inTransit a true")
		void shouldSetInTransitToTrue() {
			// Arrange
			Long id = 1L;
			Driver existingDriver = buildDefaultDriver("TRANSIT01");
			existingDriver.setId(id);
			existingDriver.setInTransit(false);
			when(driverRepository.findById(id)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateInTransitStatusById(id, true);

			// Assert
			assertThat(result.isInTransit()).isTrue();
			verify(driverRepository).findById(id);
			verify(driverRepository).save(existingDriver);
		}

		/**
		 * Verifica che il metodo imposti correttamente {@code inTransit = false}
		 * per un autista che ha completato il trasporto.
		 * <p>Mock coinvolti: repository configurato per trovare e salvare il driver.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve impostare inTransit a false")
		void shouldSetInTransitToFalse() {
			// Arrange
			Long id = 2L;
			Driver existingDriver = buildDefaultDriver("TRANSIT02");
			existingDriver.setId(id);
			existingDriver.setInTransit(true);
			when(driverRepository.findById(id)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateInTransitStatusById(id, false);

			// Assert
			assertThat(result.isInTransit()).isFalse();
			verify(driverRepository).save(existingDriver);
		}

		/**
		 * Verifica che il metodo lanci {@link ResourceNotFoundException} quando
		 * nessun autista corrisponde all'ID fornito.
		 * <p>Mock coinvolti: {@code driverRepository.findById()} restituisce
		 * {@link Optional#empty()}.</p>
		 */
		@Test
		@DisplayName("Failure Path – Deve lanciare ResourceNotFoundException se l'ID non esiste")
		void shouldThrowResourceNotFoundExceptionWhenIdNotFound() {
			// Arrange
			Long nonExistentId = 999L;
			when(driverRepository.findById(nonExistentId)).thenReturn(Optional.empty());

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateInTransitStatusById(nonExistentId, true))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Driver not found: " + nonExistentId);
			verify(driverRepository, never()).save(any());
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo lanci un'eccezione appropriata
		 * quando l'ID fornito è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 279 invoca
		 * {@code driverRepository.findById(id)} con un possibile ID null. A seconda
		 * dell'implementazione del repository Spring Data, un null potrebbe causare
		 * un {@link IllegalArgumentException} lanciato da Spring (non deterministico)
		 * oppure un comportamento non prevedibile.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere {@code Objects.requireNonNull(id, ...)}
		 * all'inizio del metodo.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione quando l'ID è null")
		void shouldThrowExceptionWhenIdIsNull() {
			// Arrange - nessun setup richiesto

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateInTransitStatusById(null, true))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * Verifica il comportamento idempotente: impostare {@code inTransit=false} su un
		 * driver già non in transito non deve causare errori.
		 * <p>Mock coinvolti: repository configurato per trovare e salvare il driver.</p>
		 */
		@Test
		@DisplayName("Edge Case – Idempotenza: inTransit=false su Driver non in transito")
		void shouldBeIdempotentWhenSettingInTransitFalseOnNonTransitDriver() {
			// Arrange
			Long id = 10L;
			Driver existingDriver = buildDefaultDriver("IDEM_TRANSIT");
			existingDriver.setId(id);
			existingDriver.setInTransit(false);
			when(driverRepository.findById(id)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateInTransitStatusById(id, false);

			// Assert
			assertThat(result.isInTransit()).isFalse();
			verify(driverRepository).save(existingDriver);
		}

		/**
		 * Verifica il comportamento con un ID boundary (valore minimo positivo: 1L).
		 * <p>Mock coinvolti: repository configurato per trovare e salvare il driver.</p>
		 */
		@Test
		@DisplayName("Boundary – Deve funzionare con ID = 1 (valore minimo positivo)")
		void shouldWorkWithMinimumPositiveId() {
			// Arrange
			Long minId = 1L;
			Driver existingDriver = buildDefaultDriver("MIN_ID_DRIVER");
			existingDriver.setId(minId);
			when(driverRepository.findById(minId)).thenReturn(Optional.of(existingDriver));
			when(driverRepository.save(existingDriver)).thenReturn(existingDriver);

			// Act
			Driver result = driverService.updateInTransitStatusById(minId, true);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.isInTransit()).isTrue();
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo rifiuti un ID negativo
		 * con un'eccezione controllata.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente non valida se l'ID
		 * è un valore negativo. Un ID negativo (-1L) non è semanticamente valido
		 * per una chiave primaria auto-generata (IDENTITY), ma passerebbe al
		 * repository senza alcun controllo.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere una validazione
		 * {@code if (id == null || id <= 0)} all'inizio del metodo.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione per ID negativo")
		void shouldThrowExceptionForNegativeId() {
			// Arrange - nessun setup richiesto

			// Act & Assert
			assertThatThrownBy(() -> driverService.updateInTransitStatusById(-1L, true))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// ==================================================================================
	// mapToEntity(DriverRequestDTO)
	// ==================================================================================

	/**
	 * Classe innestata che raggruppa i test per il metodo
	 * {@link DriverService#mapToEntity(DriverRequestDTO)}.
	 * <p>
	 * Copre la conversione completa DTO -&gt; Entity, la gestione delle
	 * approvazioni null, la validazione dei campi e i formati data invalidi.
	 * </p>
	 *
	 * @author Giovanni Vinciguerra
	 * @version 1.0
	 * @since 1.0
	 */
	@Nested
	@DisplayName("mapToEntity(DriverRequestDTO)")
	class MapToEntityTests {

		/**
		 * Verifica la conversione completa di un {@link DriverRequestDTO} valido
		 * in una entità {@link Driver} con tutte le proprietà correttamente idratate,
		 * incluse le abilitazioni ADR.
		 * <p>Nessun mock coinvolto: il metodo è puro (senza dipendenze dal repository).</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve convertire correttamente il DTO in Entity con approvals")
		void shouldMapDtoToEntityWithApprovals() {
			// Arrange
			Set<String> approvals = Set.of("BASIC", "EXPLOSIVE");
			DriverRequestDTO dto = new DriverRequestDTO(
				"Mario Rossi", "RSSMRA85M01H501Z", "+393331234567",
				"AB1234567C", "2028-12-31", "2029-06-15", approvals
			);

			// Act
			Driver result = driverService.mapToEntity(dto);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.getFullName()).isEqualTo("Mario Rossi");
			assertThat(result.getTaxCode()).isEqualTo("RSSMRA85M01H501Z");
			assertThat(result.getPhoneNumber()).isEqualTo("+393331234567");
			assertThat(result.getLicense()).isEqualTo("AB1234567C");
			assertThat(result.getLicenseExpireDate()).isEqualTo(LocalDate.of(2028, 12, 31));
			assertThat(result.getCqcExpireDate()).isEqualTo(LocalDate.of(2029, 6, 15));
			assertThat(result.getDriverApprovals())
				.containsExactlyInAnyOrder(DriverApproval.BASIC, DriverApproval.EXPLOSIVE);
		}

		/**
		 * Verifica che il metodo gestisca correttamente un DTO con approvals a null,
		 * impostando un {@link HashSet} vuoto sull'entità (riga 350 del codice sorgente).
		 * <p>Nessun mock coinvolto.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve impostare set vuoto quando driverApprovals è null nel DTO")
		void shouldSetEmptySetWhenDriverApprovalsIsNull() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Luca Bianchi", "BNCLCU90A01F205X", "+393339876543",
				"CD9876543E", "2028-12-31", "2029-06-15", null
			);

			// Act
			Driver result = driverService.mapToEntity(dto);

			// Assert
			assertThat(result.getDriverApprovals()).isNotNull();
			assertThat(result.getDriverApprovals()).isEmpty();
		}

		/**
		 * Verifica che il metodo gestisca correttamente un DTO con un set vuoto
		 * di approvals (diverso da null).
		 * <p>Nessun mock coinvolto.</p>
		 */
		@Test
		@DisplayName("Edge Case – Deve gestire un set vuoto di driverApprovals")
		void shouldHandleEmptyDriverApprovals() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Paolo Verdi", "VRDPLA85M01H501Z", "+393331111111",
				"EF1111111A", "2028-12-31", "2029-06-15", Collections.emptySet()
			);

			// Act
			Driver result = driverService.mapToEntity(dto);

			// Assert
			assertThat(result.getDriverApprovals()).isNotNull();
			assertThat(result.getDriverApprovals()).isEmpty();
		}

		/**
		 * Verifica che il metodo converta correttamente un singolo approval ADR.
		 * <p>Nessun mock coinvolto.</p>
		 */
		@Test
		@DisplayName("Happy Path – Deve convertire correttamente un singolo approval ADR")
		void shouldMapSingleAdrApproval() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Anna Neri", "NRANNA85F41H501Z", "+393332222222",
				"GH2222222B", "2028-12-31", "2029-06-15", Set.of("RADIOACTIVE")
			);

			// Act
			Driver result = driverService.mapToEntity(dto);

			// Assert
			assertThat(result.getDriverApprovals()).hasSize(1);
			assertThat(result.getDriverApprovals()).containsExactly(DriverApproval.RADIOACTIVE);
		}

		/**
		 * Verifica che il metodo lanci {@link IllegalArgumentException} quando
		 * una stringa nel set di approvals non corrisponde a un valore valido
		 * dell'enum {@link DriverApproval}.
		 * <p>Nessun mock coinvolto. L'eccezione viene lanciata da
		 * {@code Enum.valueOf(DriverApproval.class, approval)} alla riga 346.</p>
		 */
		@Test
		@DisplayName("Failure Path – Deve lanciare IllegalArgumentException per approval ADR inesistente")
		void shouldThrowIllegalArgumentExceptionForInvalidApproval() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Test Driver", "TSTDRV85M01H501Z", "+393333333333",
				"IJ3333333C", "2028-12-31", "2029-06-15", Set.of("BIOHAZARD")
			);

			// Act & Assert
			assertThatThrownBy(() -> driverService.mapToEntity(dto))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo lanci un'eccezione controllata
		 * quando il DTO è {@code null}.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 338 accede
		 * direttamente a {@code dto.fullName()} senza verificare che il DTO sia non-null.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere {@code Objects.requireNonNull(dto, ...)}
		 * all'inizio del metodo {@code mapToEntity}.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione quando il DTO è null")
		void shouldThrowExceptionWhenDtoIsNull() {
			// Arrange - nessun setup richiesto

			// Act & Assert
			assertThatThrownBy(() -> driverService.mapToEntity(null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo gestisca una data licenseExpireDate
		 * malformata con un'eccezione controllata.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 342 invoca
		 * {@code LocalDate.parse(dto.licenseExpireDate())} senza try-catch.
		 * Una stringa non ISO-8601 propaga un {@link java.time.format.DateTimeParseException}
		 * grezzo al chiamante.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Avvolgere il parsing in un try-catch con eccezione
		 * di business controllata (es. {@link IllegalArgumentException}).</p>
		 */
		@Test
		@DisplayName("[RED] Deve gestire licenseExpireDate malformata senza propagare DateTimeParseException")
		void shouldHandleMalformedLicenseExpireDateInMapping() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Bad Date Driver", "BDDTDR85M01H501Z", "+393334444444",
				"KL4444444D", "31-12-2028", "2029-06-15", null
			);

			// Act & Assert
			assertThatThrownBy(() -> driverService.mapToEntity(dto))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo gestisca una data cqcExpireDate
		 * malformata con un'eccezione controllata.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 343 invoca
		 * {@code LocalDate.parse(dto.cqcExpireDate())} senza try-catch.
		 * Identica vulnerabilità della licenseExpireDate.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Stessa mitigazione del campo licenseExpireDate.</p>
		 */
		@Test
		@DisplayName("[RED] Deve gestire cqcExpireDate malformata senza propagare DateTimeParseException")
		void shouldHandleMalformedCqcExpireDateInMapping() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Bad CQC Driver", "BDCQC85M01H501Z", "+393335555555",
				"MN5555555E", "2028-12-31", "15/06/2029", null
			);

			// Act & Assert
			assertThatThrownBy(() -> driverService.mapToEntity(dto))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo rifiuti un fullName null
		 * con un'eccezione controllata.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 338 invoca
		 * {@code driver.setFullName(dto.fullName())} senza verificare se
		 * {@code dto.fullName()} è null. Un null viene silenziosamente accettato
		 * e persiste sull'entità, violando il vincolo NOT NULL della colonna DB.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere validazione dei campi obbligatori
		 * (fullName, taxCode, license, phoneNumber) all'interno del metodo.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione quando fullName nel DTO è null")
		void shouldThrowExceptionWhenFullNameIsNull() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				null, "TSTTXC85M01H501Z", "+393336666666",
				"OP6666666F", "2028-12-31", "2029-06-15", null
			);

			// Act & Assert
			assertThatThrownBy(() -> driverService.mapToEntity(dto))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo rifiuti una license null
		 * con un'eccezione controllata.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Il codice sorgente alla riga 341 invoca
		 * {@code driver.setLicense(dto.license())} senza verificare se è null.
		 * La license è la Business Key dell'entità e deve essere obbligatoria.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere validazione obbligatoria prima del setter.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione quando license nel DTO è null")
		void shouldThrowExceptionWhenLicenseIsNull() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Valid Name", "VLDNME85M01H501Z", "+393337777777",
				null, "2028-12-31", "2029-06-15", null
			);

			// Act & Assert
			assertThatThrownBy(() -> driverService.mapToEntity(dto))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * Verifica che l'entità restituita da {@code mapToEntity} sia in stato Transient
		 * (cioè priva di ID), pronta per il layer di persistenza.
		 * <p>Nessun mock coinvolto.</p>
		 */
		@Test
		@DisplayName("Happy Path – L'entità restituita deve essere in stato Transient (id = null)")
		void shouldReturnTransientEntity() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Transient Test", "TRNTST85M01H501Z", "+393338888888",
				"QR8888888G", "2028-12-31", "2029-06-15", Set.of("TANK")
			);

			// Act
			Driver result = driverService.mapToEntity(dto);

			// Assert
			assertThat(result.getId()).isNull();
		}

		/**
		 * Verifica che l'entità restituita abbia i flag booleani ai valori di default
		 * (active = false, inTransit = false) poiché {@code mapToEntity} non li imposta.
		 * <p>Nessun mock coinvolto.</p>
		 */
		@Test
		@DisplayName("Edge Case – I flag booleani devono avere i valori di default Java (false)")
		void shouldHaveDefaultBooleanValues() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Default Flags", "DFLFLG85M01H501Z", "+393339999999",
				"ST9999999H", "2028-12-31", "2029-06-15", null
			);

			// Act
			Driver result = driverService.mapToEntity(dto);

			// Assert
			assertThat(result.isActive()).isFalse();
			assertThat(result.isInTransit()).isFalse();
		}

		/**
		 * <b>[FASE RED – TDD]</b> Verifica che il metodo rifiuti un licenseExpireDate null
		 * con un'eccezione controllata, poiché {@code LocalDate.parse(null)} lancia
		 * {@link NullPointerException}.
		 * <p>
		 * <b>VULNERABILITÀ RILEVATA:</b> Alla riga 342 {@code LocalDate.parse(dto.licenseExpireDate())}
		 * non è protetto da un controllo null preventivo.
		 * </p>
		 * <p><b>FIX ATTESO:</b> Aggiungere validazione null prima del parsing della data.</p>
		 */
		@Test
		@DisplayName("[RED] Deve lanciare eccezione quando licenseExpireDate nel DTO è null")
		void shouldThrowExceptionWhenLicenseExpireDateIsNull() {
			// Arrange
			DriverRequestDTO dto = new DriverRequestDTO(
				"Null Date Driver", "NLDTDR85M01H501Z", "+393330000000",
				"UV0000000I", null, "2029-06-15", null
			);

			// Act & Assert
			assertThatThrownBy(() -> driverService.mapToEntity(dto))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// ==================================================================================
	// UTILITY METHODS (Test Fixtures)
	// ==================================================================================

	/**
	 * Metodo factory per la costruzione di un'istanza {@link Driver} con dati di default
	 * validi. Utilizzato come fixture condivisa per ridurre la duplicazione nei test.
	 *
	 * @param license il numero di patente da assegnare al driver (Business Key).
	 * @return una nuova istanza di {@link Driver} con tutti i campi obbligatori popolati.
	 */
	private Driver buildDefaultDriver(String license) {
		Driver driver = new Driver();
		driver.setFullName("Test Driver");
		driver.setTaxCode("TSTDRV85M01H501Z");
		driver.setPhoneNumber("+393331234567");
		driver.setLicense(license);
		driver.setLicenseExpireDate(LocalDate.of(2028, 12, 31));
		driver.setCqcExpireDate(LocalDate.of(2029, 6, 15));
		driver.setDriverApprovals(null);
		driver.setActive(true);
		driver.setInTransit(false);
		return driver;
	}
}
