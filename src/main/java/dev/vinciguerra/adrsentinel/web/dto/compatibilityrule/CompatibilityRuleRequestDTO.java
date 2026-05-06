package dev.vinciguerra.adrsentinel.web.dto.compatibilityrule;

import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrClassCode;
import dev.vinciguerra.adrsentinel.web.annotation.compatibilityrule.ValidatorWarningNote;

/**
 * Data Transfer Object (DTO) immutabile, implementato nativamente come Java Record, 
 * adibito al trasporto sicuro del payload di richiesta (dal Controller REST al Service Layer) 
 * per l'orchestrazione e il salvataggio di una Regola di Compatibilità ADR.
 * <p>
 * <b>Pattern Architetturale: Flat DTO (DTO Piatto)</b><br>
 * Questo record applica rigorosamente la best practice del DTO piatto. Invece di 
 * esporre oggetti anagrafici annidati (es. intere rappresentazioni di {@code AdrClass}), 
 * richiede al client unicamente le <i>Business Key</i> (i codici identificativi). 
 * Questo approccio strategico:
 * <ul>
 * <li>Previene l'<i>Over-fetching</i>, minimizzando il peso del payload JSON sulla rete.</li>
 * <li>Azzera le ambiguità di mutazione: elimina il rischio che il frontend possa sovrascrivere 
 * per errore i dati anagrafici di una Classe ADR durante la creazione di una regola.</li>
 * <li>Garantisce un totale disaccoppiamento, rispettando il Single Responsibility Principle (SRP).</li>
 * </ul>
 * </p>
 * <p>
 * <b>Validazione Edge (Fail-Fast):</b><br>
 * Sfruttando la Constraint Composition (meta-annotazioni custom come {@code @ValidatorAdrClassCode} 
 * e {@code @ValidatorWarningNote}), il DTO sposta la validazione formale al margine esterno 
 * dell'applicazione (Edge). Le richieste malformate generano un errore 400 Bad Request istantaneo, 
 * proteggendo il Service Layer dall'elaborazione di dati non conformi o potenzialmente malevoli.
 * </p>
 * @param classCodeA   La Business Key (codice alfanumerico univoco) necessaria per identificare 
 * e recuperare dal database la prima classe ADR coinvolta. Il formato 
 * è garantito a monte dall'annotazione {@link ValidatorAdrClassCode}.
 * @param classCodeB   La Business Key necessaria per identificare la seconda classe ADR.
 * @param isCompatible Il flag operativo fondamentale di business: {@code true} indica che il 
 * carico misto sul veicolo è normativamente consentito, {@code false} 
 * impone la segregazione dei colli.
 * @param warningNote  La prescrizione operativa o nota di attenzione destinata ai documenti 
 * di viaggio (es. CMR). La sua conformità (es. limite massimo di 255 caratteri) 
 * è presidiata dall'annotazione {@link ValidatorWarningNote}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record CompatibilityRuleRequestDTO(@ValidatorAdrClassCode String classCodeA, @ValidatorAdrClassCode String classCodeB, 
	boolean isCompatible, @ValidatorWarningNote String warningNote) {}
