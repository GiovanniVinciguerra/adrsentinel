package dev.vinciguerra.adrsentinel.db.shipment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
 *
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
	/**
	 * Il mezzo di trasporto assegnato a questa specifica spedizione.
	 * <p>
	 * Relazione caricata in modo pigro ({@code LAZY}) per ottimizzare le performance di estrazione 
	 * delle spedizioni dal database.
	 * </p>
	 */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "vehicle_id",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_shipment_vehicle")
	)
	private Vehicle vehicle;
	
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
	
	public Vehicle getVehicle() {
		return vehicle;
	}
	
	public void setVehicle(Vehicle vehicle) {
		this.vehicle = vehicle;
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
			.append(destinationAddresses).append("]");
		return builder.toString();
	}
}
