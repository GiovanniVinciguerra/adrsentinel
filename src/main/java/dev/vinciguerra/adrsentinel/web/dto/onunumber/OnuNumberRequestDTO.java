package dev.vinciguerra.adrsentinel.web.dto.onunumber;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorNotRequiredString;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorRequiredString;
import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrClassCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorKemlerCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorTransportCategory;

/**
 * Data Transfer Object (DTO) immutabile, implementato come Java Record, progettato per 
 * il trasporto sicuro del payload di richiesta relativo alla creazione o all'aggiornamento 
 * di un'anagrafica Numero ONU (UN Number).
 * <p>
 * <b>Pattern Architetturale: Flat DTO & Disaccoppiamento:</b><br>
 * Il record adotta un design rigorosamente "piatto". Per la gestione della relazione con la 
 * Classe di Pericolo padre, il DTO non richiede l'intero oggetto {@code AdrClass}, ma 
 * esclusivamente la sua Business Key ({@code adrClassCode}). Questo previene vulnerabilità 
 * di <i>Mass Assignment</i> (alterazione accidentale o malevola dell'entità padre) e 
 * riduce il payload di rete (Over-fetching).
 * </p>
 * <p>
 * <b>Strategia di Sicurezza: Edge Validation (Fail-Fast):</b><br>
 * Questo DTO funge da scudo perimetrale (Edge) per il livello di Business Logic (Service). 
 * Ogni singola proprietà è presidiata da meta-annotazioni custom di Constraint Composition 
 * (es. {@code @ValidatorOnuNumberCode}, {@code @ValidatorKemlerCode}). Qualsiasi violazione 
 * formale o di dominio causerà l'interruzione immediata della richiesta (Fail-Fast) da parte 
 * del framework, restituendo un errore HTTP 400 (Bad Request) chiaro e unificato, senza mai 
 * impegnare le risorse del database.
 * </p>
 * @param onuCode La Business Key primaria: il codice standard a 4 cifre assegnato dal 
 * Comitato di Esperti dell'ONU (es. "1203", "1993").
 * @param name La denominazione ufficiale o rubrica della materia pericolosa 
 * (es. "BENZINA", "LIQUIDO INFIAMMABILE, N.A.S.").
 * @param physicalState Lo stato fisico della materia durante il trasporto (Solido, Liquido, Gas), 
 * fondamentale per la scelta degli imballaggi.
 * @param kemlerCode Il Numero di Identificazione del Pericolo (Codice Kemler), esposto 
 * nella metà superiore del pannello arancione sui veicoli (es. "33", "X88").
 * @param packingGroup Il Gruppo di Imballaggio (I, II o III) che definisce il grado di 
 * pericolosità della materia ai fini del confezionamento. Non è richiesto nella richiesta, 
 * se assente verrà automaticamente impostato il parametro più stringente {@code I}.
 * @param tunnelRestriction Il codice alfanumerico che disciplina le restrizioni di transito 
 * all'interno delle gallerie stradali (es. "D/E", "B/D"). Non è richiesto nella richiesta, 
 * se assente verrà automaticamente impostato il parametro più stringente {@code B}.
 * @param transportCategory La categoria di trasporto ADR (da 0 a 4), parametro cruciale per 
 * il calcolo delle esenzioni parziali (es. calcolo del limite di 1000 punti).
 * @param adrClassCode La chiave logica (Foreign Key di business) necessaria per risolvere 
 * il collegamento relazionale con l'entità genitore {@code AdrClass}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record OnuNumberRequestDTO(@ValidatorOnuNumberCode String onuCode, @ValidatorRequiredString String name,
	@ValidatorRequiredString String physicalState, @ValidatorKemlerCode String kemlerCode,
	@ValidatorNotRequiredString String packingGroup, @ValidatorNotRequiredString String tunnelRestriction,
	@ValidatorTransportCategory Integer transportCategory, @ValidatorAdrClassCode String adrClassCode) {}
