package dev.vinciguerra.adrsentinel.web.dto.dispatch;

import java.util.Set;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrClassCode;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;
import jakarta.validation.constraints.NotEmpty;

/**
 * Data Transfer Object (DTO) immutabile che incapsula il payload in ingresso per la fase 
 * finale del processo di dispatching: l'assegnazione degli autisti (Driver Dispatch).
 * <p>
 * Questo record agisce come contratto di comunicazione (API Contract) tra il client e 
 * il {@code DispatchService}. Riceve i dati di un trasporto già consolidato nelle fasi 
 * precedenti (veicolo selezionato e percorso generato) per innescare il motore di validazione 
 * normativo e giuslavoristico (es. doppio conducente, validità CQC e patentini ADR).
 * </p>
 * <p>
 * <b>Scelte Architetturali e Sicurezza:</b>
 * Il design di questa classe è <i>Stateless</i> e <i>Tamper-Proof</i> (A prova di manomissione). 
 * Invece di fidarsi di metriche ottenute dal client (come le ore di guida o la stazza del veicolo), 
 * il payload accetta solo chiavi di riferimento (Targa e UUID). Sarà responsabilità del Service Layer 
 * recuperare i dati reali e inviolabili dal database o dalla cache, garantendo l'assoluta 
 * aderenza legale delle assegnazioni.
 * </p>
 * @param licensePlate La targa univoca del veicolo ({@code Vehicle}) già selezionato e allocato per questo viaggio. 
 * L'annotazione custom {@code @ValidatorLicensePlate} garantisce che la stringa rispetti 
 * i pattern normativi (es. assenza di caratteri speciali, formato corretto) prima di 
 * effettuare query sul database. Dal veicolo il sistema ricaverà la massa (per il CQC) 
 * e le certificazioni strutturali (es. FL/AT per i patentini cisterna).
 * @param adrClasses L'insieme (Set) delle classi di pericolo ADR presenti a bordo per questa spedizione 
 * (es. "1", "3", "7", "8"). 
 * L'annotazione {@code @NotEmpty} blocca richieste anomale o malformate, mentre 
 * {@code @ValidatorAdrClassCode} assicura che ogni elemento dell'array sia una classe 
 * ufficialmente riconosciuta, prevenendo attacchi o corruzione del motore decisionale.
 * Questo campo è fondamentale per innescare l'obbligo di specializzazioni autista specifiche 
 * (es. {@code DriverApproval.EXPLOSIVE} per la classe 1, {@code RADIOACTIVE} per la 7).
 * @param isExempt Flag booleano che indica se l'intero carico beneficia dell'esenzione per limiti di quantità 
 * (Regola dei 1000 punti, Cap 1.1.3.6). Se {@code true}, il motore decisionale disattiverà 
 * i controlli sui patentini ADR per i conducenti, richiedendo solo la patente di guida base 
 * (e l'eventuale CQC in base al veicolo).
 * @param routeUUID L'identificatore univoco universale (UUID) che punta ai dettagli del percorso 
 * precedentemente calcolato dal motore GIS (es. HeiGIT). 
 * L'annotazione {@code @ValidatorUUID} assicura la validità formale della stringa (RFC 4122).
 * Tramite questo UUID, il backend recupererà la durata stimata del viaggio per determinare, 
 * in modo sicuro e centralizzato, la necessità di un equipaggio multiplo (> 10 ore).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record DriverDispatchRequestDTO(@ValidatorLicensePlate String licensePlate,
	@NotEmpty(message = "Malformed payload: Adr Classes cannot be empty during driver dispatch.") Set<@ValidatorAdrClassCode String> adrClasses,
	boolean isExempt, @ValidatorUUID String routeUUID) {}
