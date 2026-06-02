package dev.vinciguerra.adrsentinel.web.dto.dispatch;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorTransportMode;
import dev.vinciguerra.adrsentinel.web.annotation.dispatch.ValidatorNetWeight;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberName;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorPackingGroup;

/**
 * Data Transfer Object (DTO) immutabile progettato per incapsulare i dati di ingresso 
 * relativi a una singola partita di merce pericolosa (ADR) all'interno di una richiesta di spedizione.
 * <p>
 * In ottica Domain-Driven Design (DDD), questo record funge da livello di anti-corruzione 
 * (Anti-Corruption Layer) ai confini dell'applicazione (Presentation Layer). Il suo scopo è 
 * raccogliere i dati grezzi dal payload JSON del client e sottoporli a un rigoroso processo 
 * di validazione (Fail-Fast) prima che vengano trasformati in entità di dominio e passati 
 * al motore decisionale logistico.
 * </p>
 * <p>
 * <b>Contesto Logistico (Algoritmo di Ottimizzazione):</b><br>
 * La combinazione di questi tre campi è il requisito minimo e sufficiente per istruire 
 * l'algoritmo di routing e matchmaking:
 * <ul>
 * <li>{@code onuCode} + {@code packingGroup}: Permettono di interrogare la matrice di segregazione 
 * per calcolare le incompatibilità di carico (es. divieto di carico in comune).</li>
 * <li>{@code packingGroup} + {@code netWeight_kg}: Permettono di estrapolare la Categoria di Trasporto 
 * e calcolare il coefficiente per l'esenzione parziale (Regola dei 1000 Punti ex cap. 1.1.3.6 ADR).</li>
 * </ul>
 * </p>
 * <p>
 * <b>Scelta Architetturale:</b> L'utilizzo di un {@code record} Java garantisce la totale 
 * immutabilità dei dati in memoria (Thread-Safety), rendendo l'oggetto ideale per la 
 * deserializzazione concorrente tramite framework come Jackson.
 * </p>
 * @param onuCode Il numero ONU a 4 cifre che identifica univocamente la materia o l'oggetto 
 * pericoloso (es. "1263" per Pitture). Validato dinamicamente da 
 * {@link ValidatorOnuNumberCode} per garantirne la conformità formale.
 * @param packingGroup Il Gruppo di Imballaggio associato alla merce, che ne definisce il grado 
 * di pericolo (I = alto, II = medio, III = basso, o stringa vuota per classi 
 * prive di G.I. come la Classe 2). Validato tramite {@link ValidatorPackingGroup}.
 * @param netWeightkg La quantità netta della massa pericolosa espressa in chilogrammi. 
 * È il parametro matematico fondamentale per determinare il peso utile 
 * sul veicolo e l'applicabilità delle esenzioni. Blindato da 
 * {@link ValidatorNetWeight} contro valori nulli, negativi o anomalie IEEE 754 (NaN/Infinity).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record OnuItemRequestDTO(@ValidatorOnuNumberCode String onuCode, @ValidatorPackingGroup String packingGroup, @ValidatorOnuNumberName String name,
	@ValidatorNetWeight Integer netWeightkg, @ValidatorTransportMode String transportMode) {}
