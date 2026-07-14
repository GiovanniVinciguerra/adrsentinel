package dev.vinciguerra.adrsentinel.db.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import dev.vinciguerra.adrsentinel.db.customer.Customer;
import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.exception.BadRequestException;

/**
 * Suite di test unitari per l'entità {@link Shipment}.
 * <p>
 * Verifica le logiche di dominio interne, la validazione temporale,
 * la sanificazione degli indirizzi e i metodi helper di aggregazione.
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@DisplayName("Shipment Entity - Unit Tests")
public class ShipmentTests {

    private Shipment shipment;

    /**
     * Inizializza l'ambiente di test prima di ogni esecuzione.
     * <p>
     * Istanzia una nuova entità {@link Shipment} pulita e pronta per essere
     * configurata dai singoli metodi di test. Non impiega mock.
     * </p>
     */
    @BeforeEach
    void setUp() {
        shipment = new Shipment();
    }

    /**
     * Metodo helper per invocare l'hook JPA privato tramite reflection.
     *
     * @param target l'istanza di Shipment su cui invocare il metodo.
     * @throws Throwable l'eventuale eccezione originale sollevata dal metodo.
     */
    private void invokeOnBeforeSaveOrUpdate(Shipment target) throws Throwable {
        try {
            Method method = Shipment.class.getDeclaredMethod("onBeforeSaveOrUpdate");
            method.setAccessible(true);
            method.invoke(target);
        } catch (InvocationTargetException e) {
            throw e.getCause(); // Propaga l'eccezione originale
        } catch (Exception e) {
            throw new RuntimeException("Reflection error", e);
        }
    }

    @Nested
    @DisplayName("onBeforeSaveOrUpdate (Domain Enforcer & Normalization)")
    class OnBeforeSaveOrUpdateTests {

        /**
         * Verifica l'happy path della validazione temporale per spedizioni PLANNED.
         * <p>
         * Assicura che una spedizione creata con stato iniziale e data recente (entro
         * le 48 ore di tolleranza) superi con successo l'hook di validazione JPA.
         * Non sono presenti mock. L'output atteso è l'esecuzione silente senza eccezioni.
         * </p>
         */
        @Test
        @DisplayName("shouldPassValidationWhenPlannedAndDateIsRecent")
        void shouldPassValidationWhenPlannedAndDateIsRecent() throws Throwable {
            shipment.setShipmentStatus(ShipmentStatus.PLANNED);
            shipment.setShipmentDate(LocalDateTime.now().minusHours(24));
            
            invokeOnBeforeSaveOrUpdate(shipment);
        }

        /**
         * Verifica il fallimento della validazione temporale per spedizioni PLANNED con data troppo vecchia.
         * <p>
         * Assicura che venga sollevata una {@link BadRequestException} se la data
         * eccede la finestra di 48 ore.
         * </p>
         */
        @Test
        @DisplayName("shouldThrowBadRequestExceptionWhenPlannedAndDateIsTooOld")
        void shouldThrowBadRequestExceptionWhenPlannedAndDateIsTooOld() {
            shipment.setShipmentStatus(ShipmentStatus.PLANNED);
            shipment.setShipmentDate(LocalDateTime.now().minusDays(3));

            assertThatThrownBy(() -> invokeOnBeforeSaveOrUpdate(shipment))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("more than 48 hours in the past");
        }

        /**
         * Vulnerabilità Fix: Validazione temporale indipendente dal clock di sistema.
         * <p>
         * Inietta un Clock fisso nell'entità per simulare l'esecuzione deterministica.
         * Ci si aspetta che l'entità esponga un campo (es. 'clock') per permettere
         * il testing deterministico (TDD). Se la fix non è implementata, il test fallisce.
         * </p>
         */
        @Test
        @DisplayName("shouldUseInjectedClockForTimeValidation")
        void shouldUseInjectedClockForTimeValidation() throws Throwable {
            java.time.Clock fixedClock = java.time.Clock.fixed(
                java.time.Instant.parse("2030-01-01T10:00:00Z"), 
                java.time.ZoneId.of("UTC")
            );
            
            try {
                java.lang.reflect.Field clockField = Shipment.class.getDeclaredField("clock");
                clockField.setAccessible(true);
                clockField.set(shipment, fixedClock);
            } catch (NoSuchFieldException e) {
                org.junit.jupiter.api.Assertions.fail("La fix richiede l'aggiunta di un campo 'clock' configurabile in Shipment");
            }
            
            shipment.setShipmentStatus(ShipmentStatus.PLANNED);
            // Impostiamo la data a 3 giorni prima del 1 Gennaio 2030, che causa fallimento
            shipment.setShipmentDate(LocalDateTime.of(2029, 12, 29, 10, 0));

            assertThatThrownBy(() -> invokeOnBeforeSaveOrUpdate(shipment))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("more than 48 hours in the past");
        }

        /**
         * Verifica l'edge case: spedizioni in stato TRANSIT bypassano la validazione temporale.
         * <p>
         * Conferma che le spedizioni già operative non subiscono controlli retroattivi sulla
         * data di partenza. L'output atteso è un'esecuzione senza sollevamento di eccezioni,
         * indipendentemente dall'anzianità della data. Non sono presenti mock.
         * </p>
         */
        @Test
        @DisplayName("shouldBypassTimeValidationWhenNotInPlannedStatus")
        void shouldBypassTimeValidationWhenNotInPlannedStatus() throws Throwable {
            shipment.setShipmentStatus(ShipmentStatus.TRANSIT);
            shipment.setShipmentDate(LocalDateTime.now().minusDays(5));
            
            invokeOnBeforeSaveOrUpdate(shipment);
        }

        /**
         * Verifica la sanificazione (trimming e normalizzazione) degli indirizzi logistici.
         * <p>
         * Simula un input utente sporco con tabulazioni e ritorni a capo.
         * Verifica che l'hook JPA applichi correttamente le regex ripulendo le stringhe.
         * L'output atteso è l'assegnazione di un indirizzo pulito senza doppi spazi o caratteri di controllo.
         * Non ci sono mock coinvolti.
         * </p>
         */
        @Test
        @DisplayName("shouldNormalizeAddressesAndRemoveSpuriousSpaces")
        void shouldNormalizeAddressesAndRemoveSpuriousSpaces() throws Throwable {
            shipment.setOriginAddress("  Via \n Roma \t 1  ");
            
            List<String> destinations = new ArrayList<>();
            destinations.add(" Via \r Torino   2 ");
            shipment.setDestinationAddresses(destinations);
            
            invokeOnBeforeSaveOrUpdate(shipment);
            
            assertThat(shipment.getOriginAddress()).isEqualTo("Via Roma 1");
            assertThat(shipment.getDestinationAddresses()).containsExactly("Via Torino 2");
        }

        /**
         * Verifica che l'assenza della restrizione gallerie venga compensata col default 'B'.
         * <p>
         * Se il payload non fornisce il grado di restrizione in galleria, il sistema
         * deve difensivamente assegnare la restrizione massima. Nessun mock richiesto.
         * L'output atteso è l'assegnazione di {@link TunnelRestriction#B}.
         * </p>
         */
        @Test
        @DisplayName("shouldSetDefaultTunnelRestrictionWhenNull")
        void shouldSetDefaultTunnelRestrictionWhenNull() throws Throwable {
            shipment.setTunnelRestriction(null);
            
            invokeOnBeforeSaveOrUpdate(shipment);
            
            assertThat(shipment.getTunnelRestriction()).isEqualTo(TunnelRestriction.B);
        }
        
        /**
         * Vulnerabilità Fix: se un indirizzo di destinazione è nullo, normalize() lo ignora o lo rimuove
         * senza lanciare NullPointerException.
         * <p>
         * Simula l'inserimento di un elemento nullo all'interno della lista degli indirizzi.
         * Ci si aspetta che venga filtrato silenziosamente o ignorato.
         * </p>
         */
        @Test
        @DisplayName("shouldIgnoreNullDestinationAddressesDuringNormalization")
        void shouldIgnoreNullDestinationAddressesDuringNormalization() throws Throwable {
            List<String> destinations = new ArrayList<>();
            destinations.add(null);
            destinations.add(" Via \r Torino   2 ");
            shipment.setDestinationAddresses(destinations);
            
            // Non deve lanciare eccezioni
            invokeOnBeforeSaveOrUpdate(shipment);
            
            // Si aspetta che il null venga bypassato (es. rimosso o rimasto invariato ma l'altro pulito)
            assertThat(shipment.getDestinationAddresses()).contains("Via Torino 2");
        }
    }

    @Nested
    @DisplayName("getCustomerAsMap")
    class GetCustomerAsMapTests {

        /**
         * Verifica la corretta aggregazione degli attori logistici nella mappa unificata.
         * <p>
         * Popola integralmente le anagrafiche di Sender, Carrier e Receiver.
         * Nessun mock necessario. Il metodo deve restituire una mappa contenente tutti e 3 
         * i ruoli con i rispettivi Customer inseriti. L'output atteso è la {@link java.util.EnumMap} 
         * interamente valorizzata.
         * </p>
         */
        @Test
        @DisplayName("shouldAggregateCustomersCorrectlyWhenAllRolesPresent")
        void shouldAggregateCustomersCorrectlyWhenAllRolesPresent() {
            Customer sender = new Customer();
            sender.setId(1L);
            Customer carrier = new Customer();
            carrier.setId(2L);
            Customer receiver = new Customer();
            receiver.setId(3L);
            
            shipment.setSender(sender);
            shipment.setCarrier(carrier);
            shipment.setReceivers(List.of(receiver));
            
            Map<CustomerRole, List<Customer>> map = shipment.getCustomerAsMap();
            
            assertThat(map).containsOnlyKeys(CustomerRole.SENDER, CustomerRole.CARRIER, CustomerRole.RECEIVER);
            assertThat(map.get(CustomerRole.SENDER)).containsExactly(sender);
            assertThat(map.get(CustomerRole.CARRIER)).containsExactly(carrier);
            assertThat(map.get(CustomerRole.RECEIVER)).containsExactly(receiver);
        }

        /**
         * Verifica l'edge case in cui alcuni attori logistici non sono presenti.
         * <p>
         * Omette Sender e Receiver popolando solo il Carrier. Il metodo deve
         * omettere dalla mappa le chiavi non valorizzate senza lanciare eccezioni.
         * Nessun mock interviene. L'output atteso è una mappa con la sola chiave CARRIER.
         * </p>
         */
        @Test
        @DisplayName("shouldOnlyIncludePresentCustomersInMap")
        void shouldOnlyIncludePresentCustomersInMap() {
            Customer carrier = new Customer();
            carrier.setId(2L);
            
            shipment.setSender(null);
            shipment.setCarrier(carrier);
            shipment.setReceivers(new ArrayList<>());
            
            Map<CustomerRole, List<Customer>> map = shipment.getCustomerAsMap();
            
            assertThat(map).containsOnlyKeys(CustomerRole.CARRIER);
            assertThat(map.get(CustomerRole.CARRIER)).containsExactly(carrier);
        }
    }

    @Nested
    @DisplayName("setDrivers & setReceivers")
    class SettersTests {

        /**
         * Verifica che il setter assegni correttamente un set di autisti valido e popolato.
         * <p>
         * Simula l'assegnazione standard senza l'uso di mock. 
         * L'output atteso è che la collezione interna dell'entità coincida col parametro fornito.
         * </p>
         */
        @Test
        @DisplayName("shouldSetDriversWhenSetIsNotEmpty")
        void shouldSetDriversWhenSetIsNotEmpty() {
            Set<Driver> newDrivers = new HashSet<>();
            newDrivers.add(new Driver());
            
            shipment.setDrivers(newDrivers);
            
            assertThat(shipment.getDrivers()).isEqualTo(newDrivers);
        }

        /**
         * Vulnerabilità Fix: permette lo svuotamento o annullamento della collezione di autisti.
         * <p>
         * L'output atteso è che la collezione interna venga svuotata o nullificata,
         * rispettando il contratto tipico dei setter.
         * </p>
         */
        @Test
        @DisplayName("shouldClearDriversWhenInputIsNullOrEmpty")
        void shouldClearDriversWhenInputIsNullOrEmpty() {
            Set<Driver> initialDrivers = new HashSet<>();
            initialDrivers.add(new Driver());
            shipment.setDrivers(initialDrivers);
            
            shipment.setDrivers(null);
            assertThat(shipment.getDrivers()).isNullOrEmpty();
            
            shipment.setDrivers(initialDrivers);
            shipment.setDrivers(new HashSet<>());
            assertThat(shipment.getDrivers()).isEmpty();
        }

        /**
         * Verifica che il setter assegni correttamente una lista di destinatari valida.
         * <p>
         * Simula l'assegnazione standard. L'output atteso è l'aggiornamento
         * della lista interna dei receivers dell'entità con i dati forniti.
         * Non vi sono mock coinvolti.
         * </p>
         */
        @Test
        @DisplayName("shouldSetReceiversWhenListIsNotEmpty")
        void shouldSetReceiversWhenListIsNotEmpty() {
            List<Customer> newReceivers = new ArrayList<>();
            newReceivers.add(new Customer());
            
            shipment.setReceivers(newReceivers);
            
            assertThat(shipment.getReceivers()).isEqualTo(newReceivers);
        }

        /**
         * Vulnerabilità Fix: permette lo svuotamento o annullamento della collezione dei destinatari.
         * <p>
         * L'output atteso è che lo stato dell'entità rispecchi lo svuotamento o nullificazione.
         * </p>
         */
        @Test
        @DisplayName("shouldClearReceiversWhenInputIsNullOrEmpty")
        void shouldClearReceiversWhenInputIsNullOrEmpty() {
            List<Customer> initialReceivers = new ArrayList<>();
            initialReceivers.add(new Customer());
            shipment.setReceivers(initialReceivers);
            
            shipment.setReceivers(null);
            assertThat(shipment.getReceivers()).isNullOrEmpty();
            
            shipment.setReceivers(initialReceivers);
            shipment.setReceivers(new ArrayList<>());
            assertThat(shipment.getReceivers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("equals & hashCode")
    class EqualsAndHashCodeTests {

        /**
         * Verifica l'uguaglianza logica basata esclusivamente sulla Business Key (trackingNumber).
         * <p>
         * Inietta la stessa chiave univoca su due istanze differenti tramite reflection
         * per testare il contratto di equals e hashCode.
         * L'output atteso è un esito positivo per equals e la totale corrispondenza dell'hashCode generato.
         * </p>
         */
        @Test
        @DisplayName("shouldBeEqualWhenTrackingNumberIsSame")
        void shouldBeEqualWhenTrackingNumberIsSame() {
            Shipment s1 = new Shipment();
            Shipment s2 = new Shipment();
            
            String tracking = "TRACK-123";
            setTrackingNumberViaReflection(s1, tracking);
            setTrackingNumberViaReflection(s2, tracking);
            
            assertThat(s1).isEqualTo(s2);
            assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
        }

        /**
         * Verifica la disuguaglianza logica tra due istanze aventi Business Key differenti.
         * <p>
         * Istanzia due oggetti con tracking number autogenerati distinti al momento della creazione.
         * L'output atteso è un esito negativo per il metodo equals. Non si utilizzano mock.
         * </p>
         */
        @Test
        @DisplayName("shouldNotBeEqualWhenTrackingNumberIsDifferent")
        void shouldNotBeEqualWhenTrackingNumberIsDifferent() {
            Shipment s1 = new Shipment();
            Shipment s2 = new Shipment();
            
            assertThat(s1).isNotEqualTo(s2);
        }
        
        /**
         * Helper di reflection per bypassare l'immutabilità della Business Key
         * durante l'allestimento degli oggetti di test.
         * 
         * @param s l'entità target su cui agire
         * @param value il tracking number testuale da iniettare
         */
        private void setTrackingNumberViaReflection(Shipment s, String value) {
            try {
                java.lang.reflect.Field field = Shipment.class.getDeclaredField("trackingNumber");
                field.setAccessible(true);
                field.set(s, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
