package dev.vinciguerra.adrsentinel.web.dto.adrdispatch;

import java.util.List;

import dev.vinciguerra.adrsentinel.web.dto.onunumber.OnuNumberResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleResponseDTO;

/**
 * Data Transfer Object (DTO) immutabile che rappresenta il risultato del processo 
 * di ottimizzazione e assegnazione (Matchmaking logistico) per un singolo mezzo di trasporto.
 * <p>
 * Nel contesto architetturale di AdrSentinel, questo record incapsula la direttiva di carico 
 * definitiva per un veicolo. Viene generato dal Service Layer al termine di due fasi computazionali critiche:
 * <ol>
 * <li><b>Fase di Segregazione (Clustering):</b> Le merci raggruppate in questo DTO sono già state 
 * validate contro la matrice di incompatibilità ADR. È garantito normativamente che possano 
 * viaggiare sullo stesso mezzo (assenza di divieto di carico in comune).</li>
 * <li><b>Fase di Matchmaking:</b> Il veicolo assegnato (identificato dalla targa) possiede la 
 * portata utile e le certificazioni (es. FL, AT, EX) necessarie per questo specifico set di merci.</li>
 * </ol>
 * </p>
 * <p>
 * <b>Uso tipico nell'API:</b> Questo oggetto non viaggia quasi mai da solo, ma costituisce 
 * l'elemento portante di una collezione all'interno della response radice 
 * (es. {@code DispatchPlanResponseDTO}), mappando l'intera flotta necessaria a evadere una spedizione.
 * </p>
 * @param assignedVehicleDTO DTO del veicolo commerciale selezionato dal motore decisionale per questo specifico carico.
 * @param assignedOnuCodes La lista dei Codici ONU (es. "1263", "1090") delle merci assegnate a questo veicolo, passate come DTO.
 * Il frontend può utilizzare questa lista per generare la distinta di carico 
 * (Lettera di Vettura ADR) specifica per l'autista di questo mezzo.
 * @param totalPayloadWeightkg Il peso netto totale (in chilogrammi) delle merci pericolose caricate sul veicolo. 
 * Fondamentale per i controlli di sicurezza stradale e per evitare il sovraccarico 
 * rispetto alla massa complessiva a pieno carico consentita (PTT).
 * @param isExempt Flag booleano ad altissimo valore di business. Indica se il calcolo matematico 
 * delle Categorie di Trasporto per questo specifico carico risulta inferiore o uguale 
 * alla soglia normativa (Regola dei 1000 Punti - cap. 1.1.3.6 ADR).
 * <ul>
 * <li>{@code true}: Il trasporto gode di esenzione parziale. Non sono richiesti 
 * pannelli arancioni aperti, equipaggiamento completo, né il patentino ADR (CFP) 
 * per l'autista. Genera un forte abbattimento dei costi operativi.</li>
 * <li>{@code false}: Il trasporto è in regime ADR completo e richiede un autista 
 * specializzato e un mezzo pienamente equipaggiato.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record VehicleDispatchResponseDTO(VehicleResponseDTO assignedVehicleDTO, List<OnuNumberResponseDTO> assignedOnuCodes, Integer totalPayloadWeightkg,
	boolean isExempt) {}
