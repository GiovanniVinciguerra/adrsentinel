package dev.vinciguerra.adrsentinel.web.dto.customer;

import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorVatNumber;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object (DTO) immutabile che modella il payload in ingresso (Inbound Payload)
 * per l'aggiornamento chirurgico dello stato di operatività (attivazione o disattivazione) di un Cliente.
 * <p><b>Ruolo Architetturale e Singola Responsabilità (SRP):</b></p>
 * Questo record è progettato per gestire esclusivamente le transizioni del ciclo di vita
 * dell'entità (es. operazioni di Soft-Delete o riattivazione), tipicamente veicolate tramite
 * richieste HTTP PATCH o endpoint dedicati. Separare l'aggiornamento dello stato dal resto
 * della mutazione anagrafica (gestita da {@link CustomerUpdateDTO}) garantisce che un semplice
 * toggle operativo non esponga al rischio di sovrascritture accidentali (Mass Assignment) su
 * dati sensibili come indirizzi o ragioni sociali.
 * <p><b>Frontiera di Sicurezza e Validazione (Fail-Fast):</b></p>
 * L'unico parametro esposto è presidiato dalla constraint JSR-380 {@code @NotNull}. Questo
 * garantisce che il framework blocchi alla frontiera del Controller (HTTP 400 Bad Request)
 * qualsiasi payload vuoto, malformato o privo dell'esplicita dichiarazione di stato,
 * impedendo che valori nulli propaghino incertezze al database o alla logica di business.
 * @param vatNumber La partita iva del cliente di cui modificare i dettagli. Per motivi di sicurezza il vatNumber 
 * non è esposto come URL, ma integrato nel body della richiesta. Non viene mai modificato.
 * @param active Flag booleano esplicito che determina il nuovo stato del cliente ({@code true} per riattivare, 
 * {@code false} per sospendere/disabilitare). La presenza del dato è rigidamente garantita dall'annotazione {@code @NotNull}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record CustomerUpdateActiveStatusDTO(@ValidatorVatNumber String vatNumber,
	@NotNull(message = "Malformed payload: active status is required") Boolean active) {}
