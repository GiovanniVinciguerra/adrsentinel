package dev.vinciguerra.adrsentinel.web.dto.shipmentitem;

import dev.vinciguerra.adrsentinel.web.annotation.dispatch.ValidatorNetWeight;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberName;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorPackingGroup;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorQuantity;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorUnitOfMeasure;

/**
 * Data Transfer Object (DTO) in ingresso (Request Payload) progettato per l'aggiornamento 
 * parziale (Update/Mutation) dei dati logistici e normativi di una singola riga di carico (Shipment Item).
 * <p><b>Contesto Architetturale (Design delle API e Payload Piatto):</b></p>
 * Questo record rappresenta in modo eccellente il pattern del "Flat Payload" (Payload Piatto). 
 * Invece di richiedere al client l'invio di oggetti annidati complessi (es. l'intera entità OnuNumber) 
 * o di esporre vulnerabili ID numerici di database, l'API richiede esclusivamente le grandezze 
 * fisiche mutabili (quantità e unità di misura) e le <i>Business Keys</i> necessarie per risolvere 
 * l'anagrafica in modo sicuro a runtime (Codice ONU + Gruppo di Imballaggio). Questo minimizza 
 * l'over-fetching di rete e disaccoppia il frontend dall'infrastruttura relazionale (Hibernate).
 * <p><b>Motore di Validazione Perimetrale (Ibrido Standard/Dominio):</b></p>
 * L'integrità del payload è garantita da un'architettura di validazione ibrida e inattaccabile:
 * <ul>
 * <li><b>Grandezze Fisiche ({@code @ValidatorRequiredNumber}):</b> Assicura che la quantità 
 * sia un valore reale e strettamente positivo, impedendo anomalie nei calcoli di routing o di carico.</li>
 * <li><b>Testi Standard ({@code @ValidatorRequiredString}):</b> Mette in sicurezza i campi 
 * descrittivi bloccando stringhe vuote e prevenendo attacchi di <i>Payload Bloating</i> (max 255 char).</li>
 * <li><b>Domain Shield ({@code @ValidatorOnuNumberCode}):</b> Applica una rigorosa validazione 
 * Regex di dominio (esattamente 4 cifre), impedendo nativamente che stringhe malevole o 
 * formati errati raggiungano le query del Service Layer.</li>
 * </ul>
 * @param quantity La nuova quantità della materia caricata (deve essere rigorosamente > 0).
 * @param unitOfMeasure La nuova unità di misura associata alla quantità (es. "KG", "L").
 * @param onuCode Il codice ONU a 4 cifre esatte, utilizzato come prima chiave insieme a 
 * packingGroup e name per il lookup normativo.
 * @param packingGroup Il gruppo di imballaggio (es. "I", "II", "III"), utilizzato in combinazione 
 * con il codice ONU e il name per isolare l'esatta direttiva ADR nel database.
 * @param name Il nome della materia Onu utilizzata in combinazione con onuCode e packingGroup per isolare 
 * l'esatta direttiva ADR nel database.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentItemUpdateDTO(@ValidatorQuantity Integer quantity, @ValidatorNetWeight Integer netWeightkg, @ValidatorUnitOfMeasure String unitOfMeasure,
	@ValidatorOnuNumberCode String onuCode, @ValidatorPackingGroup String packingGroup, @ValidatorOnuNumberName String name) {}
