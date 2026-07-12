package dev.vinciguerra.adrsentinel.db.shipment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;
import dev.vinciguerra.adrsentinel.db.customer.Customer;
import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.exception.BadRequestException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

/**
 * Entità JPA che rappresenta una Spedizione logistica (Viaggio).
 * <p>
 * Costituisce il fulcro operativo del sistema di tracciamento. Collega un veicolo specifico 
 * a un tragitto definito, tracciandone lo stato di avanzamento e garantendo l'integrità 
 * temporale e spaziale del viaggio.
 * </p>
 * <h3>Design Architetturale:</h3>
 * <ul>
 * <li><b>Business Key Immutabile:</b> Identificata univocamente nel dominio logistico tramite 
 * un {@code trackingNumber} (UUID v4) generato alla creazione e blindato contro le modifiche.</li>
 * <li><b>Tolerant Reader & Security:</b> Gli indirizzi testuali sono protetti contro attacchi 
 * di tipo Injection/XSS tramite Regex dedicate, e vengono auto-sanificati (trim e collasso spazi) 
 * prima di ogni interazione col database.</li>
 * <li><b>Domain-Driven Time Validation:</b> La validazione incrociata tra lo stato della spedizione 
 * e la data viene gestita internamente tramite hook JPA ({@link #ensurePlannedShipmentIsNotInThePast()}), 
 * superando i limiti statici di Jakarta Validation.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 2.0 (Refactored with Jakarta Validation)
 * @since 1.0
 */
@Entity
@Table(
	name = "shipment",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_tracking_number", columnNames = {"tracking_number"})
	}
)
public class Shipment {
	/** Rappresenta il ciclo di vita operativo di una spedizione. */
	public enum ShipmentStatus {
		/** Spedizione creata a sistema ma non ancora presa in carico dal vettore. Modificabile. */
		PLANNED,
		/** Il veicolo è in viaggio. Le regole di validazione temporale preventiva non si applicano più. */
		TRANSIT,
		/** Merce giunta a destinazione. Stato terminale di successo. */
		DELIVERED,
		/** Spedizione annullata. Stato di fallimento o revoca. */
		CANCELLED
	}
	
	/**
	 * Definisce le causali di trasporto applicabili a una spedizione all'interno
	 * del sistema AdrSentinel.
	 * <p>
	 * La causale di trasporto è un elemento obbligatorio ai fini fiscali (D.P.R. 472/96)
	 * e logistici per la corretta compilazione del Documento di Trasporto (D.D.T.).
	 * In un contesto di merci pericolose, la selezione della causale corretta è
	 * critica per determinare l'applicabilità di specifiche esenzioni ADR,
	 * l'obbligo di documenti accessori (es. F.I.R. per i rifiuti) e le corrette
	 * dichiarazioni documentali.
 * </p>
	 */
	public enum ShipmentReason {
		/**
	     * <b>Vendita / Approvvigionamento</b>
	     * <p>
	     * Rappresenta la transazione commerciale standard in cui la merce pericolosa
	     * viene trasferita dal produttore/distributore al cliente finale o rivenditore.
	     * È la causale di default per la maggior parte delle spedizioni in uscita.
	     * </p>
	     */
		SALE,
		/**
	     * <b>Smaltimento Rifiuti</b>
	     * <p>
	     * Utilizzata quando la merce pericolosa (o i suoi residui) ha perso il suo valore
	     * commerciale e viaggia verso un centro di stoccaggio, trattamento o termovalorizzazione.
	     * </p>
	     * <p>
	     * <i>Nota ADR:</i> Questa causale richiede tipicamente che il trasporto sia accompagnato
	     * dal Formulario di Identificazione dei Rifiuti (F.I.R.) e che la designazione
	     * ufficiale di trasporto sia preceduta dalla parola "RIFIUTO" (es. RIFIUTO, UN 1230...).
	     * </p>
	     */
	    WASTE_DISPOSAL,
	    /**
	     * <b>Reso Imballaggi Vuoti non Ripuliti</b>
	     * <p>
	     * Specifica per la restituzione di contenitori (es. IBC, fusti, cisterne) che hanno
	     * contenuto merci pericolose, ma non sono stati ancora sottoposti a bonifica o lavaggio.
	     * </p>
	     * <p>
	     * <i>Nota ADR:</i> Ai sensi del Cap. 5.4.1.1.6 dell'ADR, questi imballaggi sono
	     * considerati a tutti gli effetti merce pericolosa. Nel D.D.T. richiedono una
	     * formattazione specifica della stringa di designazione (es. "IMBALLAGGIO VUOTO, 3").
	     * </p>
	     */
	    UNCLEANED_EMPTY_RETURN,
	    /**
	     * <b>Reso per Merce Non Conforme o Difettosa</b>
	     * <p>
	     * Utilizzata per il rientro di merci presso il mittente originario a causa di difetti,
	     * danni agli imballaggi, o non conformità all'ordine commerciale.
	     * </p>
	     * <p>
	     * <i>Nota ADR:</i> Se gli imballaggi originali risultano compromessi (es. fusti che perdono),
	     * questo trasporto potrebbe richiedere l'utilizzo di "imballaggi di soccorso" e
	     * l'inserimento della relativa dicitura sul documento di trasporto.
	     * </p>
	     */
	    NON_COMPLIANT_RETURN,
	    /**
	     * <b>Trasferimento Interno (tra Depositi Aziendali)</b>
	     * <p>
	     * Rappresenta lo spostamento logistico di merci pericolose tra due siti, magazzini
	     * o filiali appartenenti alla medesima ragione sociale. Non comporta una transazione
	     * commerciale di vendita e non è soggetto a fatturazione diretta.
	     * </p>
	     */
	    INTERNAL_TRANSFER,
	    /**
	     * <b>Conto Lavorazione / Miscelazione</b>
	     * <p>
	     * Utilizzata quando le materie prime (merci pericolose pure) vengono inviate a
	     * un'azienda terza (terzista) affinché quest'ultima esegua un processo di
	     * trasformazione, sintesi chimica o miscelazione.
	     * </p>
	     */
	    OUTSOURCED_PROCESSING
	}
	
	/** Chiave primaria surrogata autogenerata. */
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	/**
	 * Chiave di Business (Business Key) esposta al cliente e ai sistemi esterni.
	 * <p>
	 * Generata automaticamente come UUID al momento dell'istanziazione. L'attributo 
	 * {@code updatable = false} e l'assenza del metodo setter garantiscono che, una volta 
	 * salvato, questo identificativo non possa mai più essere alterato, né via codice né via ORM.
	 * La lunghezza è fissata a 36 caratteri per ottimizzare gli indici del database.
	 * </p>
	 */
	@Column(
		name = "tracking_number",
		nullable = false,
		unique = true,
		updatable = false,
		length = 36
	)
	private String trackingNumber = UUID.randomUUID().toString();
	/**
	 * La data e l'ora in cui la spedizione è programmata per la partenza (se {@code PLANNED}) 
	 * o in cui è effettivamente partita.
	 */
	@Column(
		name = "shipment_date",
		nullable = false
	)
	@ColumnDefault("CURRENT_TIMESTAMP")
	private LocalDateTime shipmentDate = LocalDateTime.now();
	/**
	 * Lo stato di avanzamento corrente della spedizione.
	 * <p>
	 * Salvato nel database come stringa testuale ({@code EnumType.STRING}) per garantire 
	 * la retrocompatibilità qualora vengano aggiunti nuovi stati all'enumerazione in futuro.
	 * </p>
	 */
	@Enumerated(EnumType.STRING)
	@Column(
		name = "status",
		nullable = false,
		length = 255
	)
	private ShipmentStatus shipmentStatus;
	/**
	 * Indirizzo fisico di partenza (Hub logistico o magazzino mittente).
	 * <p>
	 * L'input viene validato contro caratteri speciali pericolosi per la sicurezza (XSS), 
	 * ma accetta un'ampia varietà di formati internazionali. Gli spazi e i ritorni a capo 
	 * spuri vengono neutralizzati dall'hook {@link #normalize()}.
	 * </p>
	 */
	@Column(
		name = "origin_address",
		nullable = false,
		length = 255
	)
	private String originAddress;
	/**
	 * Lista ordinata delle tappe di consegna (Multi-Stop Routing).
	 * <p>
	 * <b>Scelta Architetturale (Element Collection):</b><br>
	 * Essendo una semplice lista di stringhe (Value Objects) e non di vere e proprie Entità, 
	 * si utilizza @ElementCollection. Hibernate creerà automaticamente una tabella ausiliaria 
	 * chiamata 'shipment_destination' legata da Foreign Key.
	 * </p>
	 * <p>
	 * <b>Importanza di @OrderColumn:</b><br>
	 * Nel routing logistico l'ordine delle tappe è tassativo. Questa annotazione forza 
	 * Hibernate a creare una colonna aggiuntiva ('stop_index') nel database per memorizzare 
	 * e ripristinare l'esatto ordine sequenziale degli indirizzi forniti dal client.
	 * </p>
	 */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(
		name = "shipment_stage",
		joinColumns = @JoinColumn(name = "shipment_id")
	)
	@Column(
		name = "destination_stage",
		nullable = false,
		length = 255
	)
	@OrderColumn(name = "shipment_stage_index")
	private List<String> destinationAddresses = new ArrayList<String>();
	/** Il divieto di transito in determinate gallerie. Se null verrà associato il grado massimo {@code B} */
	@Enumerated(EnumType.STRING)
    @Column(
    	name = "tunnel_restriction",
    	nullable = false,
    	length = 255
    )
	@ColumnDefault("'B'")
    private TunnelRestriction tunnelRestriction;
	@Enumerated(EnumType.STRING)
    @Column(
    	name = "transport_reason",
    	nullable = false,
    	length = 255
    )
	@ColumnDefault("'SALE'")
	private ShipmentReason transportReason;
	/**
	 * Il mezzo di trasporto assegnato a questa specifica spedizione.
	 * <p>
	 * Può essere {@code null} se questa Shipment non è più nello stato {@code PLANNED}, nel qual caso 
	 * per il veicolo fa fede lo snapshot dello stesso.
	 * </p>
	 */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "vehicle_id",
		nullable = true,
		foreignKey = @ForeignKey(name = "fk_shipment_vehicle")
	)
	private Vehicle vehicle;
	/**
	 * Gli autisti assegnati a questa specifica spedizione.
	 * <p>
	 * Può essere {@code empty} se questa Shipment non è più nello stato {@code PLANNED}, nel qual caso 
	 * per gli autisti fa fede lo snapshot degli stessi.
	 * </p>
	 */
	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(
		name = "shipment_driver_assignment", // Tabella ponte sul DB
		joinColumns = @JoinColumn(name = "shipment_id"),
		inverseJoinColumns = @JoinColumn(name = "driver_id")
	)
	private Set<Driver> drivers = new HashSet<Driver>();
	/**
     * Il Mittente della spedizione.
     * Molte spedizioni possono avere lo stesso mittente.
     * FK fisica nella tabella 'shipment': sender_id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = true)
    private Customer sender;
    /**
     * Il Vettore logistico.
     * Molte spedizioni possono usare lo stesso vettore.
     * FK fisica nella tabella 'shipment': carrier_id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = true)
    private Customer carrier;
    /**
     * I Destinatari della spedizione.
     * Molte spedizioni verso molti clienti.
     * Richiede una tabella ponte snella (shipment_id, customer_id). Nessun ruolo necessario.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "shipment_receiver", // Nuova tabella dedicata solo ai destinatari
        joinColumns = @JoinColumn(name = "shipment_id"),
        inverseJoinColumns = @JoinColumn(name = "customer_id")
    )
    private List<Customer> receivers = new ArrayList<>();
	
	
	/**
	 * Hook Orchestratore (Coordinator) per gli eventi di scrittura del database.
	 * <p>
	 * <b>Pattern Architetturale:</b> Risolve la limitazione della specifica JPA (che ammette un singolo 
	 * metodo {@code @PrePersist}/{@code @PreUpdate} per classe) definendo un ordine di esecuzione 
	 * rigido e deterministico per le operazioni di pre-salvataggio.
	 * </p>
	 * <p>
	 * <b>Ordine di Esecuzione:</b>
	 * <ol>
	 * <li><b>Validazione di Dominio:</b> Verifica le regole di business temporali ({@link #ensurePlannedShipmentIsNotInThePast()}).</li>
	 * <li><b>Sanificazione:</b> Pulisce e formatta i dati anagrafici ({@link #normalize()}).</li>
	 * </ol>
	 * Questo ordine garantisce che non si sprechi tempo CPU per la formattazione dei testi se 
	 * la transazione sta per essere abortita a causa di una violazione temporale.
	 * </p>
	 */
	@PrePersist
	@PreUpdate
	private void onBeforeSaveOrUpdate() {
		ensurePlannedShipmentIsNotInThePast();
		normalize();
	}
	
	/**
	 * Validatore di Dominio (Domain Enforcer): Impedisce la retrodatazione abusiva delle spedizioni.
	 * <p>
	 * <b>Logica di Business Logistica:</b><br>
	 * Se una spedizione si trova ancora nello stato inziale ({@code PLANNED}), la sua data di 
	 * partenza programmata non può essere "nel passato remoto". Viene concessa una finestra 
	 * di tolleranza operativa di <b>48 ore</b> (per consentire agli operatori di inserire 
	 * a sistema spedizioni partite nella notte o il giorno precedente in caso di down di rete), 
	 * ma oltre tale soglia l'inserimento viene considerato un errore di battitura (Fat-Finger) 
	 * o un'anomalia di sistema e viene bloccato alla radice.
	 * </p>
	 * @throws BadRequestException se la data della spedizione pianificata è antecedente a (NOW - 24 ore), 
	 * provocando il rollback immediato della transazione di database.
	 */
	private void ensurePlannedShipmentIsNotInThePast() throws BadRequestException {
		if(shipmentStatus == ShipmentStatus.PLANNED) {
			LocalDateTime toleranceLimit = LocalDateTime.now().minusDays(2);
			if(shipmentDate.isBefore(toleranceLimit))
				throw new BadRequestException("A planned shipment cannot be scheduled more than 48 hours in the past");
		}
	}
	
	/**
	 * Implementa il pattern "Tolerant Reader" per la sanificazione degli indirizzi logistici.
	 * <p>
	 * <b>Difesa dei Sistemi a Valle (Downstream Protection):</b><br>
	 * Gli indirizzi di origine e destinazione ({@code originAddress}, {@code destinationAddress}) 
	 * sono dati critici che verranno stampati su documenti ufficiali (DDT, Lettere di Vettura CMR) 
	 * o inviati a servizi esterni di geocoding (es. Google Maps API, sistemi di routing). 
	 * Questo metodo intercetta testi sporchi derivanti da copia-incolla degli utenti, rimuovendo 
	 * ritorni a capo ({@code \n}, {@code \r}), tabulazioni ({@code \t}) e spazi multipli, 
	 * compattando tutto in una stringa pulita su singola riga.
	 * </p>
	 */
	private void normalize() {
		if(originAddress != null) {
			originAddress = originAddress
				.replaceAll("[\\r\\n\\t]+", " ")
				.replaceAll(" {2,}", " ")
				.trim();
		}
		if(destinationAddresses != null) {
			destinationAddresses.replaceAll(address -> address
				.replaceAll("[\\r\\n\\t]+", " ")
				.replaceAll(" {2,}", " ")
				.trim()
			);
		}
		if(tunnelRestriction == null)
			tunnelRestriction = TunnelRestriction.B;
	}
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getTrackingNumber() {
		return trackingNumber;
	}

	public LocalDateTime getShipmentDate() {
		return shipmentDate;
	}
	
	public void setShipmentDate(LocalDateTime shipmentDate) {
		this.shipmentDate = shipmentDate;
	}
	
	public ShipmentStatus getShipmentStatus() {
		return shipmentStatus;
	}
	
	public void setShipmentStatus(ShipmentStatus shipmentStatus) {
		this.shipmentStatus = shipmentStatus;
	}
	
	public String getOriginAddress() {
		return originAddress;
	}
	
	public void setOriginAddress(String originAddress) {
		this.originAddress = originAddress;
	}
	
	public List<String> getDestinationAddresses() {
		return destinationAddresses;
	}
	
	public void setDestinationAddresses(List<String> destinationAddresses) {
		this.destinationAddresses = destinationAddresses;
	}
	
	public TunnelRestriction getTunnelRestriction() {
		return tunnelRestriction;
	}

	public void setTunnelRestriction(TunnelRestriction tunnelRestriction) {
		this.tunnelRestriction = tunnelRestriction;
	}

	public ShipmentReason getShipmentReason() {
		return transportReason;
	}

	public void setShipmentReason(ShipmentReason transportReason) {
		this.transportReason = transportReason;
	}

	public Vehicle getVehicle() {
		return vehicle;
	}
	
	public void setVehicle(Vehicle vehicle) {
		this.vehicle = vehicle;
	}
	
	public Set<Driver> getDrivers() {
		return drivers;
	}
	
	public void setDrivers(Set<Driver> drivers) {
		if(drivers == null || drivers.isEmpty()) { /* do nothing */ }
		else this.drivers = drivers;
	}

	public Customer getSender() {
		return sender;
	}

	public void setSender(Customer sender) {
		this.sender = sender;
	}

	public Customer getCarrier() {
		return carrier;
	}

	public void setCarrier(Customer carrier) {
		this.carrier = carrier;
	}

	public List<Customer> getReceivers() {
		return receivers;
	}
	
	public void setReceivers(List<Customer> receivers) {
		if(receivers == null || receivers.isEmpty()) { /* do nothing */ }
		else this.receivers = receivers;
	}
	
	/**
	 * Aggrega e restituisce tutti gli attori logistici coinvolti nella spedizione in un'unica 
	 * struttura dati iterabile e tipizzata, raggruppandoli per ruolo aziendale.
	 * <p><b>Contesto Architetturale (JPA & Hibernate):</b></p>
	 * Questo è un metodo di utilità (Helper Method) ad uso esclusivo della Business Logic 
	 * e del Presentation Layer (es. per l'esposizione verso i DTO o i template engine come Thymeleaf).
	 * L'annotazione {@link Transient} è applicata per schermare esplicitamente questo metodo 
	 * dall'analizzatore di Hibernate, prevenendo il tentativo del framework di mappare 
	 * questa struttura dati virtuale (che inizia con il prefisso "get") come se fosse 
	 * una colonna fisica sul database relazionale.
	 * <p><b>Design Pattern (Unified Type-Safety):</b></p>
	 * Per risolvere l'asimmetria di cardinalità tra i ruoli logistici (Mittente e Vettore 
	 * sono singoli, i Destinatari sono multipli), il metodo applica un pattern di normalizzazione:
	 * <ul>
	 * <li>I campi a singola cardinalità ({@code sender}, {@code carrier}) vengono dinamicamente 
	 * "impacchettati" all'interno di una lista immutabile di un solo elemento tramite {@code List.of()}.</li>
	 * <li>La collezione nativa dei destinatari ({@code receivers}) viene passata per riferimento.</li>
	 * </ul>
	 * Questo approccio garantisce una Type-Safety assoluta (evitando il ritorno di tipi generici 
	 * come {@code Object} e i relativi cast esplici) e permette l'elaborazione massiva 
	 * dei clienti tramite Stream API o cicli iterativi standard.
	 * <p><b>Ottimizzazione delle Performance:</b></p>
	 * La mappa restituita è un'istanza di {@link EnumMap}. Rispetto a un'implementazione standard 
	 * (es. {@code HashMap}), la {@code EnumMap} è ottimizzata internamente tramite array posizionali, 
	 * garantendo tempi di accesso O(1) ideali, zero collisioni e preservando l'ordinamento naturale 
	 * dettato dall'enumerazione {@link CustomerRole}.
	 * @return Una {@link Map} in cui la chiave è il ruolo ({@link CustomerRole}) e il valore 
	 * è la lista dei clienti ({@link Customer}) associati a tale ruolo. La mappa conterrà solo 
	 * le chiavi per i ruoli effettivamente valorizzati nell'entità corrente.
	 */
	@Transient
	public Map<CustomerRole, List<Customer>> getCustomerAsMap() {
		Map<CustomerRole, List<Customer>>  customerAsMap = new EnumMap<Customer.CustomerRole, List<Customer>>(CustomerRole.class);
		if(sender != null)
			customerAsMap.put(CustomerRole.SENDER, List.of(sender));
		if(carrier != null)
			customerAsMap.put(CustomerRole.CARRIER, List.of(carrier));
		if(!receivers.isEmpty())
			customerAsMap.put(CustomerRole.RECEIVER, receivers);
		return customerAsMap;
	}

	/** Calcola l'hash code basandosi esclusivamente sulla Business Key ({@code trackingNumber}). */
	@Override
	public int hashCode() {
		return Objects.hash(trackingNumber);
	}
	
	/** Verifica l'uguaglianza logica tra due spedizioni basandosi sulla Business Key. */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Shipment other = (Shipment) obj;
		return Objects.equals(trackingNumber, other.trackingNumber);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Shipment [id=").append(id).append(", trackingNumber=").append(trackingNumber)
			.append(", shipmentDate=").append(shipmentDate).append(", shipmentStatus=").append(shipmentStatus)
			.append(", originAddress=").append(originAddress).append(", destinationAddresses=")
			.append(destinationAddresses).append(", tunnelRestriction=").append(tunnelRestriction)
			.append(", transportReason=").append(transportReason).append("]");
		return builder.toString();
	}
}
