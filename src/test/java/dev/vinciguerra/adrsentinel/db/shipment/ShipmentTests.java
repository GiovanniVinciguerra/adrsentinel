package dev.vinciguerra.adrsentinel.db.shipment;

import dev.vinciguerra.adrsentinel.db.customer.Customer;
import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentReason;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Suite di test unitari per l'entità JPA {@link Shipment}.
 *
 * <p>
 * Questa classe adotta un approccio di <b>puro isolamento</b>: non viene avviato
 * alcun contesto Spring, non viene utilizzato nessun database in-memory (H2) e
 * nessun hook JPA viene eseguito automaticamente. I lifecycle hook JPA
 * ({@code @PrePersist}, {@code @PreUpdate}) vengono invocati manualmente tramite
 * reflection per testarne la logica di normalizzazione in isolamento.
 * </p>
 *
 * <h3>Metodologia: TDD Difensivo (Zero-Trust)</h3>
 * <p>
 * Oltre ai percorsi di successo (Happy Path), questa suite si concentra sui
 * percorsi di fallimento e sui casi limite. Alcuni test sono scritti
 * <b>appositamente per fallire (Fase RED del TDD)</b> e portano l'annotazione
 * {@code @DisplayName} con il prefisso "[RED TDD]". Questi test espongono
 * vulnerabilità architetturali presenti nel codice sorgente corrente che lo
 * sviluppatore è chiamato a correggere per riportarli in "verde" (Fase GREEN).
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class ShipmentTests {

    /** Istanza dell'entità sotto test, ricreata prima di ogni test. */
    private Shipment shipment;
    /** Viene usata per non scatenare controlli sulle destinazioni quando queste ultime non sono subject under test. */
    private List<String> validDestinationsParam;

    /**
     * Inizializza una nuova istanza di {@link Shipment} prima di ciascun test,
     * garantendo l'isolamento totale dello stato tra le esecuzioni.
     */
    @BeforeEach
    void setUp() {
        shipment = new Shipment();
        validDestinationsParam = new ArrayList<String>();
        validDestinationsParam.add("Via Roma, 1 Napoli (NA)");
        validDestinationsParam.add("Via Firenze, 2 Milano (MI)");
    }

    /**
     * Metodo di supporto che invoca il metodo privato {@code normalize()} dell'entità
     * tramite Java Reflection. Questo approccio è necessario poiché i lifecycle callback
     * JPA ({@code @PrePersist}/{@code @PreUpdate}) sono dichiarati {@code private} e
     * non vengono eseguiti automaticamente in assenza di un contesto JPA.
     *
     * @param target L'istanza di {@link Shipment} su cui invocare il metodo.
     * @throws Exception Se la reflection fallisce o il metodo lancia un'eccezione imprevista.
     */
    private void invokeNormalize(Shipment target) throws Exception {
        Method normalizeMethod = Shipment.class.getDeclaredMethod("normalize");
        normalizeMethod.setAccessible(true);
        normalizeMethod.invoke(target);
    }

    /**
     * Verifica le proprietà di immutabilità e unicità della Business Key
     * {@code trackingNumber} dell'entità {@link Shipment}.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("TrackingNumber — Immutabilità e Unicità della Business Key")
    class TrackingNumberTests {

        /**
         * Verifica che il {@code trackingNumber} sia generato automaticamente
         * al momento dell'istanziazione e non sia nullo né vuoto.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Stringa UUID v4 da 36 caratteri non-null.
         * </p>
         */
        @Test
        @DisplayName("Deve generare un trackingNumber non-null all'istanziazione")
        void shouldGenerateNonNullTrackingNumberOnInstantiation() {
            String trackingNumber = shipment.getTrackingNumber();
            assertThat(trackingNumber)
                .isNotNull()
                .isNotBlank()
                .hasSize(36)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        /**
         * Verifica che due istanze distinte di {@link Shipment} abbiano
         * {@code trackingNumber} differenti, garantendo l'unicità nel dominio.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> I due UUID generati devono essere diversi.
         * </p>
         */
        @Test
        @DisplayName("Deve generare un trackingNumber univoco per ogni nuova istanza")
        void shouldGenerateUniqueTrackingNumberForEachInstance() {
            Shipment shipment2 = new Shipment();
            assertThat(shipment.getTrackingNumber()).isNotEqualTo(shipment2.getTrackingNumber());
        }

        /**
         * Verifica che non esista un metodo {@code setTrackingNumber} pubblico,
         * garantendo l'immutabilità della business key a livello di API della classe.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Nessun metodo {@code setTrackingNumber} tra i metodi pubblici.
         * </p>
         */
        @Test
        @DisplayName("Non deve esporre un setter pubblico per il trackingNumber")
        void shouldNotExposePublicSetterForTrackingNumber() {
            boolean hasPublicSetter = false;
            for (Method m : Shipment.class.getMethods()) {
                if (m.getName().equals("setTrackingNumber")) {
                    hasPublicSetter = true;
                    break;
                }
            }
            assertThat(hasPublicSetter)
                .as("setTrackingNumber() non deve essere un metodo pubblico")
                .isFalse();
        }
    }

    /**
     * Verifica la logica di sanificazione del metodo privato {@code normalize()},
     * invocato tramite i lifecycle hook JPA {@code @PrePersist} e {@code @PreUpdate}.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("normalize() — Sanificazione Indirizzi e Fallback TunnelRestriction")
    class NormalizeHookTests {

        /**
         * Verifica che {@code normalize()} rimuova correttamente {@code \r}, {@code \n},
         * {@code \t} e spazi multipli dall'indirizzo di origine.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Stringa su singola riga senza caratteri di controllo.
         * </p>
         */
        @Test
        @DisplayName("Deve rimuovere \\r, \\n, \\t e spazi multipli dall'originAddress")
        void shouldSanitizeOriginAddressRemovingNewlinesAndTabs() throws Exception {
            shipment.setOriginAddress("Via Roma, 1\n\nMilano\t(MI)");
            shipment.setDestinationAddresses(validDestinationsParam);
            
            invokeNormalize(shipment);
            
            assertThat(shipment.getOriginAddress())
                .isEqualTo("Via Roma, 1 Milano (MI)")
                .doesNotContain("\n", "\r", "\t")
                .doesNotMatch(".*\\s{2,}.*");
        }

        /**
         * Verifica che {@code normalize()} gestisca correttamente i CRLF Windows-style.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> I CRLF vengono collassati in un singolo spazio.
         * </p>
         */
        @Test
        @DisplayName("Deve gestire i CRLF Windows-style nell'originAddress")
        void shouldHandleWindowsCRLFInOriginAddress() throws Exception {
            shipment.setOriginAddress("Via Verdi, 5\r\nMilano");
            shipment.setDestinationAddresses(validDestinationsParam);
            
            invokeNormalize(shipment);
            
            assertThat(shipment.getOriginAddress())
                .isEqualTo("Via Verdi, 5 Milano")
                .doesNotContain("\r", "\n");
        }

        /**
         * Verifica il comportamento null-safe di {@code normalize()} quando
         * {@code originAddress} è {@code null}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Nessuna eccezione, campo rimane {@code null}.
         * </p>
         */
        @Test
        @DisplayName("Deve gestire null in originAddress senza lanciare NullPointerException")
        void shouldHandleNullOriginAddressGracefully() throws Exception {
            shipment.setOriginAddress(null);
            shipment.setDestinationAddresses(validDestinationsParam);
            
            invokeNormalize(shipment);
            
            assertThat(shipment.getOriginAddress()).isNull();
        }

        /**
         * Verifica che {@code normalize()} rimuova elementi null dalla lista
         * {@code destinationAddresses} e sanifichi i rimanenti.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Lista senza null con indirizzi normalizzati.
         * </p>
         */
        @Test
        @DisplayName("Deve rimuovere null e sanificare le destinationAddresses")
        void shouldRemoveNullAndSanitizeDestinationAddresses() throws Exception {
            List<String> destinations = new ArrayList<>();
            destinations.add("Corso Vittorio, 10\n\tRoma");
            destinations.add(null);
            destinations.add("Piazza Navona  15");
            shipment.setDestinationAddresses(destinations);
            invokeNormalize(shipment);
            assertThat(shipment.getDestinationAddresses())
                .hasSize(2)
                .doesNotContainNull()
                .containsExactly("Corso Vittorio, 10 Roma", "Piazza Navona 15");
        }

        /**
         * Verifica che {@code normalize()} imposti {@code tunnelRestriction} al
         * valore di fallback {@link TunnelRestriction#B} quando il campo è {@code null}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code tunnelRestriction} è {@link TunnelRestriction#B}.
         * </p>
         */
        @Test
        @DisplayName("Deve impostare tunnelRestriction a B quando è null (fallback @PrePersist)")
        void shouldDefaultTunnelRestrictionToBWhenNullOnNormalize() throws Exception {
            java.lang.reflect.Field f = Shipment.class.getDeclaredField("tunnelRestriction");
            f.setAccessible(true);
            f.set(shipment, null);
            shipment.setDestinationAddresses(validDestinationsParam);
            
            invokeNormalize(shipment);
            
            assertThat(shipment.getTunnelRestriction()).isEqualTo(TunnelRestriction.B);
        }

        /**
         * Verifica che {@code normalize()} non sovrascriva un valore di
         * {@code tunnelRestriction} già impostato esplicitamente.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Il valore originale {@link TunnelRestriction#E} è preservato.
         * </p>
         */
        @Test
        @DisplayName("Non deve sovrascrivere tunnelRestriction se già valorizzata")
        void shouldNotOverwriteTunnelRestrictionWhenAlreadySet() throws Exception {
            shipment.setTunnelRestriction(TunnelRestriction.E);
            shipment.setDestinationAddresses(validDestinationsParam);
            
            invokeNormalize(shipment);
            
            assertThat(shipment.getTunnelRestriction()).isEqualTo(TunnelRestriction.E);
        }

        /**
         * Verifica che un indirizzo di soli spazi diventi stringa vuota dopo {@code normalize()}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Stringa vuota {@code ""}.
         * </p>
         */
        @Test
        @DisplayName("Un originAddress di soli spazi deve sollevare eccezione")
        void shouldThrowIllegalArgumentExceptionIfOriginAddressHasOnlySpaces() throws Exception {
            shipment.setOriginAddress("     ");
            assertThatThrownBy(() -> invokeNormalize(shipment))
            	.isInstanceOf(InvocationTargetException.class)
            	.hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Verifica la correttezza di tutti i getter e setter standard dell'entità {@link Shipment}.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Getter e Setter — Correttezza del Mapping dei Campi")
    class GetterSetterTests {

        /**
         * Verifica che {@code setId()} e {@code getId()} funzionino correttamente.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Il valore impostato viene restituito identico.
         * </p>
         */
        @Test
        @DisplayName("setId/getId deve persistere la chiave primaria")
        void shouldPersistIdViaSetterGetter() {
            shipment.setId(42L);
            assertThat(shipment.getId()).isEqualTo(42L);
        }

        /**
         * Verifica che {@code setShipmentDate()} e {@code getShipmentDate()} operino
         * correttamente sulla data di spedizione.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> La data impostata viene restituita invariata.
         * </p>
         */
        @Test
        @DisplayName("setShipmentDate/getShipmentDate deve persistere la data")
        void shouldPersistShipmentDateViaSetterGetter() {
            LocalDateTime expectedDate = LocalDateTime.of(2026, 7, 20, 9, 0);
            shipment.setShipmentDate(expectedDate);
            assertThat(shipment.getShipmentDate()).isEqualTo(expectedDate);
        }

        /**
         * Verifica che la data di spedizione abbia un valore di default non-null
         * impostato al momento dell'istanziazione.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code getShipmentDate()} non restituisce {@code null}.
         * </p>
         */
        @Test
        @DisplayName("La shipmentDate deve avere un default non-null all'istanziazione")
        void shouldHaveNonNullDefaultShipmentDate() {
            assertThat(shipment.getShipmentDate()).isNotNull();
        }

        /**
         * Verifica che {@code setShipmentStatus()} e {@code getShipmentStatus()}
         * gestiscano correttamente tutti i valori dell'enumerazione {@link ShipmentStatus}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Ogni valore dell'enum è impostato e restituito.
         * </p>
         */
        @Test
        @DisplayName("setShipmentStatus/getShipmentStatus deve persistere ogni valore dell'enum")
        void shouldPersistAllShipmentStatusValues() {
            for (ShipmentStatus status : ShipmentStatus.values()) {
                shipment.setShipmentStatus(status);
                assertThat(shipment.getShipmentStatus()).as("Valore atteso: %s", status).isEqualTo(status);
            }
        }

        /**
         * Verifica che {@code setOriginAddress()} e {@code getOriginAddress()}
         * persistano correttamente un indirizzo valido.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> L'indirizzo impostato viene restituito identico.
         * </p>
         */
        @Test
        @DisplayName("setOriginAddress/getOriginAddress deve persistere l'indirizzo")
        void shouldPersistOriginAddressViaSetterGetter() {
            String expected = "Via Garibaldi, 100, 20100 Milano MI, Italia";
            shipment.setOriginAddress(expected);
            assertThat(shipment.getOriginAddress()).isEqualTo(expected);
        }

        /**
         * Verifica che {@code setDestinationAddresses()} e {@code getDestinationAddresses()}
         * preservino l'ordine delle tappe (ordinamento è tassativo nel routing logistico).
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Lista restituita nell'ordine esatto.
         * </p>
         */
        @Test
        @DisplayName("setDestinationAddresses/getDestinationAddresses deve preservare l'ordine delle tappe")
        void shouldPreserveDestinationAddressesOrder() {
            List<String> addresses = List.of("Tappa 1 - Roma", "Tappa 2 - Napoli", "Tappa 3 - Salerno");
            shipment.setDestinationAddresses(new ArrayList<>(addresses));
            assertThat(shipment.getDestinationAddresses()).containsExactlyElementsOf(addresses);
        }

        /**
         * Verifica che {@code getDestinationAddresses()} restituisca una lista
         * vuota non-null appena dopo l'istanziazione dell'entità.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Lista vuota, mai {@code null}.
         * </p>
         */
        @Test
        @DisplayName("getDestinationAddresses deve restituire lista vuota di default")
        void shouldReturnEmptyListByDefaultForDestinationAddresses() {
            assertThat(shipment.getDestinationAddresses()).isNotNull().isEmpty();
        }

        /**
         * Verifica che {@code setShipmentReason()} e {@code getShipmentReason()}
         * persistano correttamente tutti i valori di {@link ShipmentReason}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Ogni valore dell'enum è impostato e restituito.
         * </p>
         */
        @Test
        @DisplayName("setShipmentReason/getShipmentReason deve persistere ogni causale di trasporto")
        void shouldPersistAllShipmentReasonValues() {
            for (ShipmentReason reason : ShipmentReason.values()) {
                shipment.setShipmentReason(reason);
                assertThat(shipment.getShipmentReason()).as("Causale attesa: %s", reason).isEqualTo(reason);
            }
        }

        /**
         * Verifica che {@code setVehicle()} e {@code getVehicle()} persistano
         * il riferimento al veicolo assegnato alla spedizione.
         * <p>
         * <b>Mock coinvolti:</b> {@link Vehicle} mockato.<br>
         * <b>Output atteso:</b> Il mock restituito è lo stesso oggetto impostato.
         * </p>
         */
        @Test
        @DisplayName("setVehicle/getVehicle deve persistere il riferimento al veicolo")
        void shouldPersistVehicleReference() {
            Vehicle mockVehicle = mock(Vehicle.class);
            shipment.setVehicle(mockVehicle);
            assertThat(shipment.getVehicle()).isSameAs(mockVehicle);
        }

        /**
         * Verifica che {@code setVehicle(null)} accetti {@code null} senza eccezioni,
         * coerentemente con il campo {@code nullable=true} nella colonna JPA.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code getVehicle()} restituisce {@code null}.
         * </p>
         */
        @Test
        @DisplayName("setVehicle(null) deve essere accettato (vehicle è nullable)")
        void shouldAcceptNullVehicle() {
            shipment.setVehicle(mock(Vehicle.class));
            shipment.setVehicle(null);
            assertThat(shipment.getVehicle()).isNull();
        }

        /**
         * Verifica che {@code setSender()} e {@code getSender()} persistano
         * il riferimento al mittente della spedizione.
         * <p>
         * <b>Mock coinvolti:</b> {@link Customer} mockato.<br>
         * <b>Output atteso:</b> Il mock restituito è lo stesso oggetto impostato.
         * </p>
         */
        @Test
        @DisplayName("setSender/getSender deve persistere il riferimento al mittente")
        void shouldPersistSenderReference() {
            Customer mockSender = mock(Customer.class);
            shipment.setSender(mockSender);
            assertThat(shipment.getSender()).isSameAs(mockSender);
        }

        /**
         * Verifica che {@code setCarrier()} e {@code getCarrier()} persistano
         * il riferimento al vettore logistico della spedizione.
         * <p>
         * <b>Mock coinvolti:</b> {@link Customer} mockato.<br>
         * <b>Output atteso:</b> Il mock restituito è lo stesso oggetto impostato.
         * </p>
         */
        @Test
        @DisplayName("setCarrier/getCarrier deve persistere il riferimento al vettore")
        void shouldPersistCarrierReference() {
            Customer mockCarrier = mock(Customer.class);
            shipment.setCarrier(mockCarrier);
            assertThat(shipment.getCarrier()).isSameAs(mockCarrier);
        }
    }

    /**
     * Verifica il comportamento difensivo del metodo {@link Shipment#setDrivers(Set)},
     * che sostituisce un parametro {@code null} con un {@link HashSet} vuoto.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("setDrivers() — Comportamento Difensivo Null-Safe")
    class SetDriversDefensiveTests {

        /**
         * Verifica che {@code setDrivers(null)} inizializzi il campo {@code drivers}
         * con un {@link HashSet} vuoto invece di impostare il riferimento a {@code null}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Set non-null e vuoto.
         * </p>
         */
        @Test
        @DisplayName("setDrivers(null) deve inizializzare drivers a HashSet vuoto")
        void shouldInitializeDriversToEmptySetWhenNullPassed() {
            shipment.setDrivers(null);
            assertThat(shipment.getDrivers()).isNotNull().isEmpty();
        }

        /**
         * Verifica che {@code setDrivers()} con un set valido sostituisca
         * correttamente il set degli autisti.
         * <p>
         * <b>Mock coinvolti:</b> {@link Driver} mockato.<br>
         * <b>Output atteso:</b> Il set contiene esattamente l'autista fornito.
         * </p>
         */
        @Test
        @DisplayName("setDrivers(set valido) deve sostituire il set degli autisti")
        void shouldReplaceDriversSetWithValidNonNullSet() {
            Driver mockDriver = mock(Driver.class);
            Set<Driver> expectedDrivers = new HashSet<>();
            expectedDrivers.add(mockDriver);
            shipment.setDrivers(expectedDrivers);
            assertThat(shipment.getDrivers()).isNotNull().hasSize(1).contains(mockDriver);
        }

        /**
         * Verifica che {@code getDrivers()} restituisca un set vuoto non-null
         * immediatamente dopo l'istanziazione dell'entità.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Set non-null e vuoto.
         * </p>
         */
        @Test
        @DisplayName("getDrivers deve restituire un set vuoto non-null di default")
        void shouldReturnEmptyNonNullSetByDefault() {
            assertThat(shipment.getDrivers()).isNotNull().isEmpty();
        }
    }

    /**
     * Verifica il comportamento difensivo del metodo {@link Shipment#setReceivers(List)},
     * che sostituisce un parametro {@code null} con una {@link ArrayList} vuota.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("setReceivers() — Comportamento Difensivo Null-Safe")
    class SetReceiversDefensiveTests {

        /**
         * Verifica che {@code setReceivers(null)} inizializzi il campo {@code receivers}
         * con una {@link ArrayList} vuota invece di impostare il riferimento a {@code null}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Lista non-null e vuota.
         * </p>
         */
        @Test
        @DisplayName("setReceivers(null) deve inizializzare receivers a ArrayList vuota")
        void shouldInitializeReceiversToEmptyListWhenNullPassed() {
            shipment.setReceivers(null);
            assertThat(shipment.getReceivers()).isNotNull().isEmpty();
        }

        /**
         * Verifica che {@code setReceivers()} con una lista valida sostituisca
         * correttamente la lista dei destinatari.
         * <p>
         * <b>Mock coinvolti:</b> Due {@link Customer} mockati.<br>
         * <b>Output atteso:</b> La lista contiene esattamente i due destinatari forniti.
         * </p>
         */
        @Test
        @DisplayName("setReceivers(lista valida) deve sostituire la lista dei destinatari")
        void shouldReplaceReceiversListWithValidNonNullList() {
            Customer receiver1 = mock(Customer.class);
            Customer receiver2 = mock(Customer.class);
            shipment.setReceivers(new ArrayList<>(List.of(receiver1, receiver2)));
            assertThat(shipment.getReceivers()).isNotNull().hasSize(2).containsExactlyInAnyOrder(receiver1, receiver2);
        }

        /**
         * Verifica che {@code getReceivers()} restituisca una lista vuota non-null
         * immediatamente dopo l'istanziazione dell'entità.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Lista non-null e vuota.
         * </p>
         */
        @Test
        @DisplayName("getReceivers deve restituire una lista vuota non-null di default")
        void shouldReturnEmptyNonNullListByDefault() {
            assertThat(shipment.getReceivers()).isNotNull().isEmpty();
        }
    }

    /**
     * Verifica il comportamento difensivo del metodo
     * {@link Shipment#setTunnelRestriction(TunnelRestriction)},
     * che applica un fallback a {@link TunnelRestriction#B} quando il parametro è {@code null}.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("setTunnelRestriction() — Fallback Null-Safe a TunnelRestriction.B")
    class SetTunnelRestrictionDefensiveTests {

        /**
         * Verifica che {@code setTunnelRestriction(null)} imposti il campo al
         * valore di fallback {@link TunnelRestriction#B} senza lanciare eccezioni.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code getTunnelRestriction()} restituisce {@link TunnelRestriction#B}.
         * </p>
         */
        @Test
        @DisplayName("setTunnelRestriction(null) deve applicare il fallback a B (principio fail-safe)")
        void shouldFallbackToBWhenNullTunnelRestrictionPassed() {
            shipment.setTunnelRestriction(null);
            assertThat(shipment.getTunnelRestriction()).isEqualTo(TunnelRestriction.B);
        }

        /**
         * Verifica che {@code setTunnelRestriction()} imposti correttamente ogni
         * valore valido dell'enumerazione {@link TunnelRestriction}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Il valore impostato viene restituito invariato.
         * </p>
         */
        @Test
        @DisplayName("setTunnelRestriction deve persistere ogni valore valido dell'enum")
        void shouldPersistAllValidTunnelRestrictionValues() {
            for (TunnelRestriction restriction : TunnelRestriction.values()) {
                shipment.setTunnelRestriction(restriction);
                assertThat(shipment.getTunnelRestriction()).as("Restrizione attesa: %s", restriction).isEqualTo(restriction);
            }
        }

        /**
         * Verifica che il valore {@link TunnelRestriction#NONE} (nessuna restrizione)
         * sia impostabile senza che il sistema applichi il fallback.
         * NONE è un valore valido dell'enum (non è null), quindi non deve essere
         * sostituito con B.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code getTunnelRestriction()} restituisce {@link TunnelRestriction#NONE}.
         * </p>
         */
        @Test
        @DisplayName("setTunnelRestriction(NONE) deve essere preservato — NONE non è null")
        void shouldPreserveNoneRestrictionWithoutFallback() {
            shipment.setTunnelRestriction(TunnelRestriction.NONE);
            assertThat(shipment.getTunnelRestriction()).isEqualTo(TunnelRestriction.NONE);
        }
    }

    /**
     * Verifica il comportamento del metodo {@link Shipment#getCustomerAsMap()},
     * un helper transient che aggrega gli attori logistici in una {@link java.util.EnumMap}
     * tipizzata per ruolo ({@link CustomerRole}).
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("getCustomerAsMap() — Aggregazione Tipizzata degli Attori Logistici")
    class GetCustomerAsMapTests {

        /** Mock del mittente della spedizione. */
        @Mock
        private Customer mockSender;

        /** Mock del vettore logistico della spedizione. */
        @Mock
        private Customer mockCarrier;

        /** Mock del destinatario della spedizione. */
        @Mock
        private Customer mockReceiver;

        /**
         * Happy Path completo: verifica che quando tutti e tre i ruoli logistici
         * sono valorizzati la mappa contenga esattamente tre chiavi.
         * <p>
         * <b>Mock coinvolti:</b> {@code mockSender}, {@code mockCarrier}, {@code mockReceiver}.<br>
         * <b>Output atteso:</b> Mappa con chiavi SENDER, CARRIER, RECEIVER.
         * </p>
         */
        @Test
        @DisplayName("Happy Path: deve restituire una mappa con SENDER, CARRIER e RECEIVER se tutti valorizzati")
        void shouldReturnMapWithAllThreeRolesWhenAllSet() {
            shipment.setSender(mockSender);
            shipment.setCarrier(mockCarrier);
            shipment.setReceivers(new ArrayList<>(List.of(mockReceiver)));
            Map<CustomerRole, List<Customer>> result = shipment.getCustomerAsMap();
            assertThat(result).isNotNull().containsKeys(CustomerRole.SENDER, CustomerRole.CARRIER, CustomerRole.RECEIVER).hasSize(3);
            assertThat(result.get(CustomerRole.SENDER)).containsExactly(mockSender);
            assertThat(result.get(CustomerRole.CARRIER)).containsExactly(mockCarrier);
            assertThat(result.get(CustomerRole.RECEIVER)).containsExactly(mockReceiver);
        }

        /**
         * Verifica che la chiave SENDER sia omessa se il sender è null.
         * <p>
         * <b>Mock coinvolti:</b> {@code mockCarrier}.<br>
         * <b>Output atteso:</b> Mappa con sole chiavi CARRIER e RECEIVER.
         * </p>
         */
        @Test
        @DisplayName("Deve omettere la chiave SENDER dalla mappa se sender è null")
        void shouldOmitSenderKeyWhenSenderIsNull() {
            shipment.setSender(null);
            shipment.setCarrier(mockCarrier);
            shipment.setReceivers(new ArrayList<>(List.of(mockReceiver)));
            Map<CustomerRole, List<Customer>> result = shipment.getCustomerAsMap();
            assertThat(result).doesNotContainKey(CustomerRole.SENDER).containsKeys(CustomerRole.CARRIER, CustomerRole.RECEIVER);
        }

        /**
         * Verifica che la chiave CARRIER sia omessa se il carrier è null.
         * <p>
         * <b>Mock coinvolti:</b> {@code mockSender}, {@code mockReceiver}.<br>
         * <b>Output atteso:</b> Mappa con sole chiavi SENDER e RECEIVER.
         * </p>
         */
        @Test
        @DisplayName("Deve omettere la chiave CARRIER dalla mappa se carrier è null")
        void shouldOmitCarrierKeyWhenCarrierIsNull() {
            shipment.setSender(mockSender);
            shipment.setCarrier(null);
            shipment.setReceivers(new ArrayList<>(List.of(mockReceiver)));
            Map<CustomerRole, List<Customer>> result = shipment.getCustomerAsMap();
            assertThat(result).doesNotContainKey(CustomerRole.CARRIER).containsKeys(CustomerRole.SENDER, CustomerRole.RECEIVER);
        }

        /**
         * Verifica che la chiave RECEIVER sia omessa se la lista receivers è vuota.
         * <p>
         * <b>Mock coinvolti:</b> {@code mockSender}, {@code mockCarrier}.<br>
         * <b>Output atteso:</b> Mappa con sole chiavi SENDER e CARRIER.
         * </p>
         */
        @Test
        @DisplayName("Deve omettere la chiave RECEIVER dalla mappa se la lista receivers è vuota")
        void shouldOmitReceiverKeyWhenReceiversListIsEmpty() {
            shipment.setSender(mockSender);
            shipment.setCarrier(mockCarrier);
            Map<CustomerRole, List<Customer>> result = shipment.getCustomerAsMap();
            assertThat(result).doesNotContainKey(CustomerRole.RECEIVER).containsKeys(CustomerRole.SENDER, CustomerRole.CARRIER);
        }

        /**
         * Edge Case: verifica che la mappa sia vuota quando nessun attore è valorizzato.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Mappa non-null ma completamente vuota.
         * </p>
         */
        @Test
        @DisplayName("Edge Case: deve restituire una mappa vuota se nessun attore logistico è valorizzato")
        void shouldReturnEmptyMapWhenNoCustomerIsSet() {
            Map<CustomerRole, List<Customer>> result = shipment.getCustomerAsMap();
            assertThat(result).isNotNull().isEmpty();
        }

        /**
         * Verifica che la mappa restituita sia un'istanza di {@link java.util.EnumMap},
         * garantendo l'ottimizzazione O(1) senza collisioni.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> La mappa è un'istanza di {@link java.util.EnumMap}.
         * </p>
         */
        @Test
        @DisplayName("La mappa restituita deve essere un'istanza di EnumMap (ottimizzazione O(1))")
        void shouldReturnEnumMapInstance() {
            Map<CustomerRole, List<Customer>> result = shipment.getCustomerAsMap();
            assertThat(result).isInstanceOf(java.util.EnumMap.class);
        }

        /**
         * Verifica che la lista SENDER nella mappa sia immutabile (List.of()),
         * impedendo modifiche accidentali all'attore logistico.
         * <p>
         * <b>Mock coinvolti:</b> {@code mockSender}.<br>
         * <b>Output atteso:</b> {@link UnsupportedOperationException} al tentativo di aggiunta.
         * </p>
         */
        @Test
        @DisplayName("La lista SENDER nella mappa deve essere immutabile (List.of())")
        void shouldReturnImmutableListForSenderInMap() {
            shipment.setSender(mockSender);
            Map<CustomerRole, List<Customer>> result = shipment.getCustomerAsMap();
            List<Customer> senderList = result.get(CustomerRole.SENDER);
            assertThatThrownBy(() -> senderList.add(mock(Customer.class)))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        /**
         * Verifica che la lista CARRIER nella mappa sia immutabile (List.of()),
         * impedendo modifiche accidentali all'attore logistico.
         * <p>
         * <b>Mock coinvolti:</b> {@code mockCarrier}.<br>
         * <b>Output atteso:</b> {@link UnsupportedOperationException} al tentativo di aggiunta.
         * </p>
         */
        @Test
        @DisplayName("La lista CARRIER nella mappa deve essere immutabile (List.of())")
        void shouldReturnImmutableListForCarrierInMap() {
            shipment.setCarrier(mockCarrier);
            Map<CustomerRole, List<Customer>> result = shipment.getCustomerAsMap();
            List<Customer> carrierList = result.get(CustomerRole.CARRIER);
            assertThatThrownBy(() -> carrierList.add(mock(Customer.class)))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        /**
         * Verifica che con più destinatari la lista RECEIVER contenga tutti
         * i destinatari nell'ordine corretto.
         * <p>
         * <b>Mock coinvolti:</b> Tre {@link Customer} mockati.<br>
         * <b>Output atteso:</b> La lista RECEIVER contiene esattamente i tre destinatari.
         * </p>
         */
        @Test
        @DisplayName("Deve includere tutti i destinatari nella lista RECEIVER della mappa")
        void shouldIncludeAllReceiversInMapReceiverList() {
            Customer r1 = mock(Customer.class);
            Customer r2 = mock(Customer.class);
            Customer r3 = mock(Customer.class);
            shipment.setReceivers(new ArrayList<>(List.of(r1, r2, r3)));
            Map<CustomerRole, List<Customer>> result = shipment.getCustomerAsMap();
            assertThat(result.get(CustomerRole.RECEIVER)).containsExactly(r1, r2, r3);
        }
    }

    /**
     * Verifica il contratto di uguaglianza ({@code equals} e {@code hashCode})
     * dell'entità {@link Shipment}, basato esclusivamente sulla Business Key
     * ({@code trackingNumber}).
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("equals() e hashCode() — Contratto sulla Business Key (trackingNumber)")
    class EqualsHashCodeTests {

        /**
         * Verifica la proprietà riflessiva del contratto equals.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code shipment.equals(shipment)} è {@code true}.
         * </p>
         */
        @Test
        @DisplayName("Proprietà riflessiva: una Shipment deve essere uguale a se stessa")
        void shouldBeEqualToItself() {
            assertThat(shipment).isEqualTo(shipment);
        }

        /**
         * Verifica che {@code equals(null)} restituisca {@code false}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code false}.
         * </p>
         */
        @Test
        @DisplayName("equals(null) deve restituire false")
        void shouldNotBeEqualToNull() {
            assertThat(shipment).isNotEqualTo(null);
        }

        /**
         * Verifica che due istanze con trackingNumber diversi non siano uguali.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code false}.
         * </p>
         */
        @Test
        @DisplayName("Due Shipment con trackingNumber diversi non devono essere uguali")
        void shouldNotBeEqualWhenTrackingNumbersDiffer() {
            Shipment other = new Shipment();
            assertThat(shipment).isNotEqualTo(other);
        }

        /**
         * Verifica che due istanze con lo stesso trackingNumber siano uguali
         * e abbiano lo stesso hashCode.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code equals()} restituisce {@code true} e hashCode coincidono.
         * </p>
         */
        @Test
        @DisplayName("Due Shipment con lo stesso trackingNumber devono essere uguali")
        void shouldBeEqualWhenTrackingNumbersMatch() throws Exception {
            Shipment other = new Shipment();
            String sharedTracking = shipment.getTrackingNumber();
            java.lang.reflect.Field f = Shipment.class.getDeclaredField("trackingNumber");
            f.setAccessible(true);
            f.set(other, sharedTracking);
            assertThat(shipment).isEqualTo(other);
            assertThat(shipment.hashCode()).isEqualTo(other.hashCode());
        }

        /**
         * Verifica che {@code equals()} restituisca {@code false} con oggetto di tipo diverso.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> {@code false}.
         * </p>
         */
        @Test
        @DisplayName("equals() deve restituire false se confrontato con un oggetto di tipo diverso")
        void shouldNotBeEqualToObjectOfDifferentType() {
            assertThat(shipment).isNotEqualTo("non_sono_una_shipment");
        }

        /**
         * Verifica che due istanze diverse abbiano hash code diversi.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> Hash code diversi.
         * </p>
         */
        @Test
        @DisplayName("Due Shipment diversi devono avere hash code diversi")
        void shouldHaveDifferentHashCodesForDifferentTrackingNumbers() {
            Shipment other = new Shipment();
            assertThat(shipment.hashCode()).isNotEqualTo(other.hashCode());
        }
    }

    /**
     * Verifica il comportamento del metodo {@link Shipment#toString()}.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("toString() — Rappresentazione Testuale dell'Entità")
    class ToStringTests {

        /**
         * Verifica che {@code toString()} restituisca una stringa non-null che include
         * i campi chiave dell'entità.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> La stringa contiene le label identificative dei campi.
         * </p>
         */
        @Test
        @DisplayName("toString deve includere i campi chiave dell'entità")
        void shouldIncludeKeyFieldsInToString() {
            shipment.setId(1L);
            shipment.setOriginAddress("Via Test, 1");
            shipment.setShipmentStatus(ShipmentStatus.PLANNED);
            String result = shipment.toString();
            assertThat(result)
                .isNotNull()
                .contains("Shipment [")
                .contains("id=")
                .contains("trackingNumber=")
                .contains("shipmentDate=")
                .contains("shipmentStatus=")
                .contains("originAddress=")
                .contains("destinationAddresses=")
                .contains("tunnelRestriction=")
                .contains("transportReason=");
        }

        /**
         * Verifica che {@code toString()} includa il valore effettivo del {@code trackingNumber}.
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso:</b> La stringa contiene il valore del trackingNumber.
         * </p>
         */
        @Test
        @DisplayName("toString deve includere il valore effettivo del trackingNumber")
        void shouldIncludeActualTrackingNumberValueInToString() {
            String expectedTracking = shipment.getTrackingNumber();
            String result = shipment.toString();
            assertThat(result).contains(expectedTracking);
        }
    }

    /**
     * <b>ATTENZIONE — TEST RED TDD: VULNERABILITA' ARCHITETTURALI ESPOSTE</b>
     *
     * <p>
     * Questa classe contiene test scritti <b>appositamente per FALLIRE</b> con il codice
     * sorgente corrente di {@link Shipment}. Rappresentano la <b>Fase RED del ciclo TDD</b>
     * e hanno l'obiettivo di esporre vulnerabilità architetturali, ovvero controlli logici
     * o validazioni mancanti nella classe.
     * </p>
     *
     * <p>
     * <b>Istruzione per lo Sviluppatore:</b> Per rendere "verde" ciascun test, implementare
     * il controllo mancante indicato nel Javadoc del singolo metodo. Nessun test in questa
     * classe deve essere eliminato o modificato per adattarlo al comportamento difettoso
     * del codice attuale.
     * </p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("[RED TDD] Vulnerabilita' Architetturali Esposte")
    class RedTddVulnerabilityTests {

        /**
         * <b>[RED TDD] Vulnerabilita' #2: Assenza di validazione su originAddress vuoto post-normalizzazione.</b>
         *
         * <p>
         * <b>Falla rilevata:</b> Il setter {@code setOriginAddress()} accetta qualsiasi stringa.
         * Dopo normalize(), un indirizzo di soli spazi diventa stringa vuota senza lanciare eccezioni.
         * Un indirizzo vuoto rende impossibile il geocoding HeiGIT e la generazione del DDT,
         * causando un errore silenzioso a runtime a valle nel Service Layer.
         * </p>
         *
         * <p>
         * <b>Correzione necessaria:</b> Aggiungere in {@code normalize()} un controllo
         * che lanci {@link IllegalArgumentException} se dopo la normalizzazione
         * {@code originAddress} risulta nullo o vuoto.
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso (dopo correzione):</b> {@link IllegalArgumentException} con "originAddress".
         * </p>
         */
        @Test
        @DisplayName("[RED TDD] #2: normalize() deve rifiutare un originAddress che diventa vuoto dopo sanificazione")
        void shouldRejectOriginAddressThatBecomesEmptyAfterNormalization() throws Exception {
            // Arrange
            shipment.setOriginAddress("   \n\t\r   "); // solo whitespace -> diventa "" dopo normalize()

            // Act & Assert
            // QUESTO TEST FALLIRA' con il codice attuale.
            // normalize() non lancia eccezioni su stringhe vuote post-trim.
            assertThatThrownBy(() -> invokeNormalize(shipment))
            	.isInstanceOf(InvocationTargetException.class)
            	.hasCauseInstanceOf(IllegalArgumentException.class);
        }

        /**
         * <b>[RED TDD] Vulnerabilita' #3: Assenza di validazione sulla lista destinationAddresses vuota.</b>
         *
         * <p>
         * <b>Falla rilevata:</b> Non esiste alcun controllo che impedisca di persistere
         * una spedizione con lista destinazioni completamente vuota. Un viaggio senza
         * tappe di destinazione è impossibile da pianificare con HeiGIT e da documentare nel DDT.
         * </p>
         *
         * <p>
         * <b>Correzione necessaria:</b> Aggiungere in {@code normalize()} un controllo
         * che lanci {@link IllegalStateException} se la lista {@code destinationAddresses}
         * è vuota o contiene solo stringhe vuote dopo la normalizzazione.
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso (dopo correzione):</b> {@link IllegalStateException} con "destinationAddresses".
         * </p>
         */
        @Test
        @DisplayName("[RED TDD] #3: normalize() deve rifiutare una lista destinationAddresses vuota")
        void shouldRejectEmptyDestinationAddressesList() throws Exception {
            // Arrange
            shipment.setOriginAddress("Via Valida, 1");
            shipment.setDestinationAddresses(new ArrayList<>()); // lista vuota

            // Act & Assert
            // QUESTO TEST FALLIRA' con il codice attuale.
            // normalize() non verifica che la lista destinazioni sia non-vuota dopo la pulizia.
            assertThatThrownBy(() -> invokeNormalize(shipment))
            	.isInstanceOf(InvocationTargetException.class)
            	.hasCauseInstanceOf(IllegalStateException.class);
        }

        /**
         * <b>[RED TDD] Vulnerabilita' #4: Mutabilita' esterna del set drivers restituito da getDrivers().</b>
         *
         * <p>
         * <b>Falla rilevata:</b> Il metodo {@code getDrivers()} restituisce il riferimento
         * diretto al {@code HashSet} interno. Un chiamante esterno può modificare il set
         * (aggiungere/rimuovere autisti) senza passare per il setter, aggirando qualsiasi
         * futura validazione. Viola il principio di incapsulamento del Rich Domain Model.
         * </p>
         *
         * <p>
         * <b>Correzione necessaria:</b> {@code getDrivers()} deve restituire una copia
         * difensiva ({@code new HashSet<>(drivers)}) o una vista immutabile
         * ({@code Collections.unmodifiableSet(drivers)}).
         * </p>
         *
         * <p>
         * <b>Mock coinvolti:</b> Nessuno.<br>
         * <b>Output atteso (dopo correzione):</b> {@link UnsupportedOperationException}.
         * </p>
         */
        @Test
        @DisplayName("[RED TDD] #4: getDrivers() non deve restituire un riferimento mutabile al set interno")
        void shouldPreventExternalMutabilityOfDriversSet() {
            // Arrange
            Driver mockDriver = mock(Driver.class);
            shipment.setDrivers(new HashSet<>(Set.of(mockDriver)));

            // Act
            Set<Driver> returnedDrivers = shipment.getDrivers();

            // Assert
            // QUESTO TEST FALLIRA' con il codice attuale.
            // getDrivers() restituisce il HashSet interno direttamente modificabile.
            assertThatThrownBy(() -> returnedDrivers.add(mock(Driver.class)))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
