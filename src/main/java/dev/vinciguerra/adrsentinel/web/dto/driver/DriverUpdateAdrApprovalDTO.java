package dev.vinciguerra.adrsentinel.web.dto.driver;

import java.util.Set;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorDriverApprovals;

/**
 * Data Transfer Object (DTO) immutabile dedicato al trasporto delle informazioni necessarie 
 * per l'aggiornamento massivo delle abilitazioni ADR di un conducente (Driver).
 * <p><b>Contesto Architetturale (Pattern DTO & Immutabilità):</b></p>
 * Implementato sfruttando il costrutto {@code record} nativo di Java, questo componente agisce 
 * come un contenitore dati passivo (Data Carrier) e thread-safe. Il suo scopo principale è 
 * disaccoppiare la rappresentazione esterna della risorsa (il payload JSON consumato dal 
 * Presentation Layer/Controller REST) dal modello di dominio interno (Entità JPA {@code Driver}).
 * </p>
 * <p>
 * L'utilizzo di un record garantisce l'immutabilità strutturale del payload una volta concluso 
 * il processo di de-serializzazione (tipicamente gestito da Jackson in Spring Boot), impedendo 
 * alterazioni accidentali o malevole dello stato durante l'attraversamento dei vari layer 
 * applicativi (Controller -> Service).
 * </p>
 * @param approvals La collezione contenente i codici alfanumerici delle nuove certificazioni ADR 
 * (es. base, cisterne, esplosivi) destinate a sovrascrivere l'attuale profilo del conducente.
 * <br><br>
 * <b>Meccanismo di Validazione (Edge Security):</b><br>
 * La presenza dell'annotazione {@link ValidatorDriverApprovals} a livello di parametro 
 * istruisce il framework di validazione (Hibernate Validator) a intercettare il set 
 * <b>nella sua interezza</b>. Prima che il Controller possa elaborare la richiesta, 
 * l'intero gruppo di stringhe viene sottoposto alla <i>Deep Inspection</i> del validatore 
 * custom, garantendo una protezione <i>Fail-Fast</i> contro payload contenenti valori nulli, 
 * stringhe vuote o codici non mappabili nel dizionario enumerato del dominio ADR.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 */
public record DriverUpdateAdrApprovalDTO(@ValidatorDriverApprovals Set<String> approvals) {}
