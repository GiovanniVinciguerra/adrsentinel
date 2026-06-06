package dev.vinciguerra.adrsentinel.web.dto.shipment;

import java.util.List;

import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleResponseDTO;

/**
 * Data Transfer Object (DTO) in uscita (Response Payload) per l'esposizione sicura e 
 * strutturata dei dettagli di una spedizione di merci pericolose (ADR).
 * <p><b>Contesto Architetturale (API Contract e Information Hiding):</b></p>
 * Questo record rappresenta la "vista" pubblica dell'aggregato logistico. Il suo scopo 
 * principale è disaccoppiare il modello relazionale del database (Entity) dal livello 
 * di presentazione (Controller). Grazie a questo DTO, il sistema espone all'esterno 
 * solo i dati strettamente necessari per le interfacce utente, nascondendo logiche di 
 * persistenza, campi sensibili di audit (es. createdBy, modifiedAt) e proteggendo 
 * l'infrastruttura da vulnerabilità di tipo <i>Mass Assignment</i>.
 * <p><b>Scelta Architetturale (Java Record & Ottimizzazione JSON):</b></p>
 * L'implementazione come {@code record} garantisce che la risposta HTTP sia costruita su 
 * un oggetto immutabile e Thread-Safe. Inoltre, i record godono di una serializzazione 
 * JSON estremamente rapida ed efficiente tramite le librerie standard (es. Jackson), 
 * poiché riducono al minimo l'overhead in memoria e l'utilizzo della Reflection.
 * @param trackingNumber La Business Key pubblica (UUID). È il vero identificatore di dominio 
 * da fornire ai clienti per il tracciamento esterno della spedizione.
 * @param shipmentDate Il marcatore temporale (Data e Ora locale) della pianificazione logistica. 
 * Verrà automaticamente formattato in ISO 8601 dal serializzatore JSON.
 * @param shipmentStatus Lo stato corrente della spedizione all'interno del suo ciclo di vita 
 * (Macchina a Stati). Viene serializzato in stringa in modo nativo dall'Enum.
 * @param originAddress L'indirizzo toponomastico del sito di prelievo del carico ADR.
 * @param destinationAddresses Gli indirizzi toponomastici dei siti di consegna.
 * @param distancekm La distanza calcolata in chilometri della rotta logistica.
 * @param vehicle L'oggetto rappresentante il mezzo della flotta assegnato a questo trasporto.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record ShipmentResponseDTO(String trackingNumber, String shipmentDate, String shipmentStatus,
		String originAddress, List<String> destinationAddresses, VehicleResponseDTO vehicle) {
	
	/**
	 * Factory Method statico per la conversione (Mapping) e l'aggregazione strutturata 
	 * di un'entità di dominio {@link Shipment} nel suo corrispondente Data Transfer Object 
	 * in uscita {@link ShipmentResponseDTO}.
	 * <p><b>Contesto Architetturale (Information Hiding e API Contract):</b></p>
	 * Questo metodo rappresenta l'ultimo "casello" prima che i dati lascino il backend. 
	 * Il suo compito è disaccoppiare in modo definitivo l'infrastruttura di persistenza (Hibernate) 
	 * dal Presentation Layer (Controller), assicurando che il client riceva un JSON piatto, 
	 * prevedibile e purificato da qualsiasi metadato relazionale (es. proxy JPA).
	 * <p><b>Design Pattern e Strategie di Serializzazione:</b></p>
	 * <ul>
	 * <li><b>Guard Clause (Null-Safety):</b> L'implementazione si apre con un controllo difensivo 
	 * ({@code if(entity == null)}), garantendo stabilità durante il mapping massivo di collezioni 
	 * (es. impaginazione via Stream API).</li>
	 * <li><b>Serializzazione Forte (Type Flattening):</b> I tipi complessi nativi di Java vengono 
	 * forzati in stringhe per garantire massima compatibilità JSON con i client esterni:
	 * <ul>
	 * <li>Il marcatore temporale viene serializzato tramite {@code .toString()}, producendo 
	 * una stringa standard ISO-8601 blindata, prevenendo anomalie di formattazione di Jackson.</li>
	 * <li>Lo stato logistico (Enum) viene estratto tramite {@code .name()}, disaccoppiando 
	 * il payload dalla classe Java sottostante.</li>
	 * </ul>
	 * </li>
	 * <li><b>Mapping Annidato (Protezione dal Loop):</b> Invece di iniettare l'entità complessa 
	 * del Veicolo, la risoluzione viene delegata al factory method {@link VehicleResponseDTO#fromEntity}. 
	 * Questo blocca alla radice qualsiasi rischio di <i>StackOverflowError</i> (Infinite Loop) 
	 * o <i>LazyInitializationException</i> durante la serializzazione JSON.</li>
	 * </ul>
	 * @param entity L'istanza dell'entità JPA recuperata dal database, rappresentante la 
	 * singola spedizione logistica ADR. Ammette valori {@code null}.
	 * @return Una nuova istanza immutabile (Record) di {@link ShipmentResponseDTO} pronta 
	 * per l'invio HTTP, oppure {@code null} se l'input fornito era assente.
	 */
	public static ShipmentResponseDTO fromEntity(Shipment entity) {
		if(entity == null)
			return null;
		
		return new ShipmentResponseDTO(
			entity.getTrackingNumber(),
			entity.getShipmentDate().toString(),
			entity.getShipmentStatus().name(),
			entity.getOriginAddress(),
			entity.getDestinationAddresses(),
			entity.getVehicleSnapshot() != null ?
				VehicleResponseDTO.fromEntity(entity.getVehicleSnapshot()) :
				VehicleResponseDTO.fromEntity(entity.getVehicle())
		);
	}
}
