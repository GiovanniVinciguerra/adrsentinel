package dev.vinciguerra.adrsentinel.web.dto.shipment;

import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;

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
 * @param id L'identificatore tecnico primario (Surrogate Key) del database. Utile al client 
 * per la gestione di griglie dati o framework reattivi lato frontend.
 * @param trackingNumber La Business Key pubblica (UUID). È il vero identificatore di dominio 
 * da fornire ai clienti per il tracciamento esterno della spedizione.
 * @param shipmentDate Il marcatore temporale (Data e Ora locale) della pianificazione logistica. 
 * Verrà automaticamente formattato in ISO 8601 dal serializzatore JSON.
 * @param shipmentStatus Lo stato corrente della spedizione all'interno del suo ciclo di vita 
 * (Macchina a Stati). Viene serializzato in stringa in modo nativo dall'Enum.
 * @param originAddress L'indirizzo toponomastico del sito di prelievo del carico ADR.
 * @param destinationAddress L'indirizzo toponomastico del sito di consegna.
 * @param distancekm La distanza calcolata in chilometri della rotta logistica.
 * @param vehicle L'oggetto rappresentante il mezzo della flotta assegnato a questo trasporto.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record ShipmentResponseDTO(Long id, String trackingNumber, String shipmentDate, ShipmentStatus shipmentStatus,
	String originAddress, String destinationAddress, Float distancekm, Vehicle vehicle) {}
