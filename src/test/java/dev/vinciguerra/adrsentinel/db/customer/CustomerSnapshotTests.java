package dev.vinciguerra.adrsentinel.db.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;

/**
 * Classe di unit test dedicata all'entità {@link CustomerSnapshot}.
 * <p>
 * Il testing viene eseguito in puro isolamento senza l'ausilio di framework di persistenza (come H2 o Spring Data),
 * utilizzando Mockito e AssertJ. Vengono testati gli Happy Path, i Failure Path e sono inseriti deliberatamente
 * test in fase RED per documentare debolezze di design e assenza di validazioni interne.
 * </p>
 * 
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class CustomerSnapshotTests {

	/**
	 * Test di gruppo (Nested) per i costruttori della classe {@link CustomerSnapshot}.
	 */
	@Nested
	@DisplayName("Tests for CustomerSnapshot Constructors")
	class ConstructorTests {

		@Mock
		private Customer mockCustomer;

		/**
		 * Verifica la corretta cristallizzazione dei dati anagrafici dal master (Customer) allo snapshot.
		 * <p>
		 * Happy path in cui l'oggetto viene costruito con un Customer valido e un Ruolo valido.
		 * </p>
		 */
		@Test
		@DisplayName("Should create snapshot successfully with valid customer and role")
		void givenValidCustomerAndRole_whenInstantiated_thenFieldsAreCopiedCorrectly() {
			// Arrange
			when(mockCustomer.getCompanyName()).thenReturn("Acme Corp");
			when(mockCustomer.getVatNumber()).thenReturn("IT12345678901");
			when(mockCustomer.getLegalAddress()).thenReturn("Via Roma 1, Milano");
			
			// Act
			CustomerSnapshot snapshot = new CustomerSnapshot(mockCustomer, CustomerRole.SENDER);
			
			// Assert
			assertThat(snapshot.getCompanyNameSnap()).isEqualTo("Acme Corp");
			assertThat(snapshot.getVatNumberSnap()).isEqualTo("IT12345678901");
			assertThat(snapshot.getLegalAddressSnap()).isEqualTo("Via Roma 1, Milano");
			assertThat(snapshot.getRoleSnap()).isEqualTo(CustomerRole.SENDER);
		}

		/**
		 * Verifica che l'inizializzazione con Customer nullo lanci un'eccezione, per proteggere
		 * l'integrità del sistema.
		 */
		@Test
		@DisplayName("Should throw exception when Customer is null")
		void givenNullCustomer_whenInstantiated_thenThrowsIllegalArgumentException() {
			// Act & Assert
			assertThatThrownBy(() -> new CustomerSnapshot(null, CustomerRole.SENDER))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Customer is null");
		}

		/**
		 * Verifica che l'inizializzazione con Ruolo nullo lanci un'eccezione, assicurando che lo
		 * snapshot abbia sempre un significato logistico.
		 */
		@Test
		@DisplayName("Should throw exception when Role is null")
		void givenNullRole_whenInstantiated_thenThrowsIllegalArgumentException() {
			// Act & Assert
			assertThatThrownBy(() -> new CustomerSnapshot(mockCustomer, null))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Role is null");
		}

		/**
		 * <b>FASE RED (Vulnerabilità Documentata):</b>
		 * Il costruttore di CustomerSnapshot copia acriticamente i valori da Customer.
		 * Se un Customer ha dati incompleti (es. null), questi fluiscono direttamente nello snapshot,
		 * aggirando i vincoli di non nullità (nullable=false) definiti nelle colonne JPA.
		 * <p>
		 * Questo test è attualmente progettato per fallire e dimostrare la necessità di validazioni aggiuntive.
		 * </p>
		 */
		@Test
		@DisplayName("RED TEST: Should fail instantiation if Customer internal state is incomplete")
		void givenCustomerWithNullProperties_whenInstantiated_thenThrowsIllegalArgumentException() {
			// Arrange
			when(mockCustomer.getCompanyName()).thenReturn(null);
			
			// Act & Assert
			// Questo costrutto si aspetta un'eccezione a causa della mancanza di dati, ma la classe 
			// la ammette e perciò il test andrà in RED evidenziando la vulnerabilità.
			assertThatThrownBy(() -> new CustomerSnapshot(mockCustomer, CustomerRole.SENDER))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Unable to create customer snapshot.");
		}
	}

	/**
	 * Test di gruppo (Nested) per il factory method {@link CustomerSnapshot#fromCustomers(Shipment)}.
	 */
	@Nested
	@DisplayName("Tests for static factory method fromCustomers")
	class FromCustomersFactoryTests {

		@Mock
		private Shipment mockShipment;

		/**
		 * Verifica l'Happy Path in cui una spedizione fornisce una mappa di attori logistici valida
		 * e il sistema genera correttamente la mole di snapshot prevista, assegnando la Shipment a ognuno.
		 */
		@Test
		@DisplayName("Should build set of CustomerSnapshots from Shipment")
		void givenValidShipmentWithCustomers_whenFromCustomers_thenReturnSetOfSnapshots() {
			// Arrange
			Customer mockSender = mock(Customer.class);
			when(mockSender.getVatNumber()).thenReturn("IT00000000001");
			when(mockSender.getCompanyName()).thenReturn("Automotive SPA");
			when(mockSender.getLegalAddress()).thenReturn("Via dei Finzi, Milano");
			Customer mockReceiver = mock(Customer.class);
			when(mockReceiver.getVatNumber()).thenReturn("IT00000000002");
			when(mockReceiver.getCompanyName()).thenReturn("Robotics SRL");
			when(mockReceiver.getLegalAddress()).thenReturn("Via delle Contesse, Roma");
			
			Map<CustomerRole, List<Customer>> customerMap = new EnumMap<>(CustomerRole.class);
			customerMap.put(CustomerRole.SENDER, List.of(mockSender));
			customerMap.put(CustomerRole.RECEIVER, List.of(mockReceiver));
			
			when(mockShipment.getCustomerAsMap()).thenReturn(customerMap);
			
			// Act
			Set<CustomerSnapshot> snapshots = CustomerSnapshot.fromCustomers(mockShipment);
			
			// Assert
			assertThat(snapshots)
				.isNotNull()
				.hasSize(2)
				.allMatch(snap -> snap.getShipment() == mockShipment)
				.extracting(CustomerSnapshot::getRoleSnap)
					.containsExactlyInAnyOrder(CustomerRole.SENDER, CustomerRole.RECEIVER);
		}

		/**
		 * Verifica la protezione contro il passaggio di un riferimento Shipment nullo al factory method.
		 */
		@Test
		@DisplayName("Should throw exception when Shipment is null")
		void givenNullShipment_whenFromCustomers_thenThrowsIllegalArgumentException() {
			// Act & Assert
			assertThatThrownBy(() -> CustomerSnapshot.fromCustomers(null))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Shipment is null");
		}

		/**
		 * Verifica la gestione di un Shipment privo di clienti associati. Il factory method
		 * deve interrompere la creazione per evitare snapshot privi di valore.
		 */
		@Test
		@DisplayName("Should throw exception when Shipment customers map is empty")
		void givenShipmentWithEmptyCustomersMap_whenFromCustomers_thenThrowsIllegalArgumentException() {
			// Arrange
			when(mockShipment.getCustomerAsMap()).thenReturn(new EnumMap<>(CustomerRole.class));
			
			// Act & Assert
			assertThatThrownBy(() -> CustomerSnapshot.fromCustomers(mockShipment))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("map is null or empty");
		}

		/**
		 * Verifica la gestione di un Shipment che restituisce null al posto della mappa, garantendo
		 * robustezza contro implementazioni difettose.
		 */
		@Test
		@DisplayName("Should throw exception when Shipment customers map is null")
		void givenShipmentWithNullCustomersMap_whenFromCustomers_thenThrowsIllegalArgumentException() {
			// Arrange
			when(mockShipment.getCustomerAsMap()).thenReturn(null);
			
			// Act & Assert
			assertThatThrownBy(() -> CustomerSnapshot.fromCustomers(mockShipment))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("map is null or empty");
		}

		/**
		 * <b>FASE RED (Vulnerabilità Documentata):</b>
		 * Cosa accade se la mappa restituita da `getCustomerAsMap` contiene liste con elementi `null`?
		 * Poiché il `flatMap` e il costruttore verranno chiamati, questo porterà a null pointer o
		 * all'instanziazione fallita in maniera subdola. Documentiamo che fromCustomers 
		 * dovrebbe gestire questo Edge Case difensivamente o lanciare eccezioni semantiche.
		 */
		@Test
		@DisplayName("RED TEST: Should handle defensively or throw meaningful error if map contains null customer")
		void givenShipmentWithMapContainingNullCustomer_whenFromCustomers_thenThrowsExpectedException() {
			// Arrange
			Map<CustomerRole, List<Customer>> corruptedMap = new EnumMap<>(CustomerRole.class);
			// Mettiamo un valore nullo ad arte, supportato da java.util.ArrayList
			java.util.List<Customer> listWithNull = new java.util.ArrayList<>();
			listWithNull.add(null);
			corruptedMap.put(CustomerRole.CARRIER, listWithNull);
			
			when(mockShipment.getCustomerAsMap()).thenReturn(corruptedMap);
			
			// Act & Assert
			// Fallirà con NullPointerException o l'eccezione del costruttore, idealmente ci aspetteremmo 
			// che fallisca a livello di Stream saltando o intercettando l'errore chiaramente per la singola entità.
			assertThatThrownBy(() -> CustomerSnapshot.fromCustomers(mockShipment))
					.isInstanceOf(IllegalArgumentException.class) // Il costruttore la lancerà, ma noi stiamo asserendo il comportamento d'insieme.
					.hasMessageContaining("Unable to create customer snapshot");
		}
	}

	/**
	 * Test di gruppo (Nested) per l'uguaglianza logica basata sulla Business Key (vatNumberSnap).
	 */
	@Nested
	@DisplayName("Tests for Equals and HashCode")
	class EqualsAndHashCodeTests {

		/**
		 * Verifica che due snapshot con la stessa partita iva siano considerati uguali, anche se hanno 
		 * ruoli o dettagli marginali differenti, seguendo il principio della "Business Key Equality".
		 */
		@Test
		@DisplayName("Should evaluate as equals when vatNumberSnap is the same")
		void givenSameVatNumber_whenEquals_thenReturnsTrue() {
			// Arrange
			Customer c1 = mock(Customer.class);
			when(c1.getVatNumber()).thenReturn("IT111");
			when(c1.getCompanyName()).thenReturn("Automotive SPA");
			when(c1.getLegalAddress()).thenReturn("Via dei Finzi, Milano");
			
			Customer c2 = mock(Customer.class);
			when(c2.getVatNumber()).thenReturn("IT111");
			when(c2.getCompanyName()).thenReturn("Robotics SRL");
			when(c2.getLegalAddress()).thenReturn("Via delle Contesse, Roma");
			
			CustomerSnapshot s1 = new CustomerSnapshot(c1, CustomerRole.SENDER);
			CustomerSnapshot s2 = new CustomerSnapshot(c2, CustomerRole.RECEIVER);
			
			// Act & Assert
			assertThat(s1).isEqualTo(s2);
		}

		/**
		 * Verifica che snapshot di aziende diverse non risultino uguali.
		 */
		@Test
		@DisplayName("Should evaluate as not equals when vatNumberSnap is different")
		void givenDifferentVatNumber_whenEquals_thenReturnsFalse() {
			// Arrange
			Customer c1 = mock(Customer.class);
			when(c1.getVatNumber()).thenReturn("IT111");
			when(c1.getCompanyName()).thenReturn("Automotive SPA");
			when(c1.getLegalAddress()).thenReturn("Via dei Finzi, Milano");
			
			Customer c2 = mock(Customer.class);
			when(c2.getVatNumber()).thenReturn("IT222");
			when(c2.getCompanyName()).thenReturn("Robotics SRL");
			when(c2.getLegalAddress()).thenReturn("Via delle Contesse, Roma");
			
			CustomerSnapshot s1 = new CustomerSnapshot(c1, CustomerRole.SENDER);
			CustomerSnapshot s2 = new CustomerSnapshot(c2, CustomerRole.SENDER);
			
			// Act & Assert
			assertThat(s1).isNotEqualTo(s2);
		}

		/**
		 * Assicura che l'hashcode sia coerente con la regola di uguaglianza (Business Key).
		 */
		@Test
		@DisplayName("Should have same hash code when vatNumberSnap is the same")
		void givenSameVatNumber_whenHashCode_thenAreEquals() {
			// Arrange
			Customer c1 = mock(Customer.class);
			when(c1.getVatNumber()).thenReturn("IT111");
			when(c1.getCompanyName()).thenReturn("Automotive SPA");
			when(c1.getLegalAddress()).thenReturn("Via dei Finzi, Milano");
			
			Customer c2 = mock(Customer.class);
			when(c2.getVatNumber()).thenReturn("IT111");
			when(c2.getCompanyName()).thenReturn("Robotics SRL");
			when(c2.getLegalAddress()).thenReturn("Via delle Contesse, Roma");
			
			CustomerSnapshot s1 = new CustomerSnapshot(c1, CustomerRole.SENDER);
			CustomerSnapshot s2 = new CustomerSnapshot(c2, CustomerRole.RECEIVER);
			
			// Act & Assert
			assertThat(s1.hashCode()).hasSameHashCodeAs(s2.hashCode());
		}
	}
}
