package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator;

import java.util.regex.Pattern;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorOnuPackingCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del validatore custom legato all'annotazione {@link ValidatorOnuPackingCode}.
 * <p><b>Contesto Architetturale (Anti-Corruption Layer testuale):</b></p>
 * Questa classe opera allo strato di trasporto (es. validazione dei DTO in ingresso). 
 * Il suo compito è agire da filtro sintattico primario: garantisce che le stringhe fornite 
 * come codici ONU di imballaggio rispettino formalmente la "grammatica" imposta dalla 
 * normativa ADR e i limiti fisici del database, respingendo input spuri (HTTP 400) 
 * prima che raggiungano la logica di business.
 * <p><b>Separazione delle Responsabilità (Sintassi vs Semantica):</b></p>
 * Questo validatore si occupa <b>esclusivamente della validazione sintattica</b> 
 * (es. presenza di lettere, numeri, separatori ammessi e lunghezze massime). 
 * Le validazioni semantiche (es. la rimozione di codici duplicati o le logiche di auto-fix) 
 * sono delegate ai metodi del ciclo di vita (es. {@code @PrePersist}) dell'entità di dominio.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorOnuPackingCode
 */
public class OnuPackingCodeValidator implements ConstraintValidator<ValidatorOnuPackingCode, String> {
	/**
	 * Espressione Regolare (Pattern) precompilata per la validazione formale della sintassi ADR.
	 * Il pattern è thread-safe e precompilato per ottimizzare le performance in esecuzione.
	 * * <p><b>Analisi del Pattern (Motore Regex):</b></p>
	 * <ul>
	 * <li>{@code ^(?=.{2,15}$)} - <i>Database Safeguard (Lookahead Positivo):</i> Verifica istantaneamente 
	 * che l'intera stringa sia lunga tra 2 e 15 caratteri. Questo previene in modo matematico 
	 * l'errore SQL di {@code DataTruncation} sulla colonna VARCHAR del database.</li>
	 * <li>{@code ([1-9]|[1-3][0-9])} - <i>Start Rule:</i> Il codice deve obbligatoriamente iniziare 
	 * con un numero valido (1-9 per colli standard, 10-39 per IBC/Grandi Imballaggi).</li>
	 * <li>{@code [A-Za-z]{1,2}} - <i>Material Rule:</i> Richiede una o due lettere (case-insensitive) 
	 * che identificano il materiale dell'imballaggio.</li>
	 * <li>{@code [0-9]?} - <i>Variant Rule:</i> Consente un numero finale opzionale per la specifica costruttiva.</li>
	 * <li>{@code (?:[,\\-] ... ){0,3}$} - <i>Multiplier Rule:</i> Gruppo non catturante che permette 
	 * l'inserimento fino a un massimo di 3 codici aggiuntivi (4 totali), separati rigidamente da virgola o trattino.</li>
	 * </ul>
	 */
	private final static Pattern ONU_PACKING_CODE_PATTERN = Pattern.compile(
		"^(?=.{2,15}$)([1-9]|[1-3][0-9])[A-Za-z]{1,2}[0-9]?(?:[,\\-]([1-9]|[1-3][0-9])[A-Za-z]{1,2}[0-9]?){0,3}$"
	);
	
	/**
	 * Esegue la validazione effettiva sulla stringa di input.
	 * <p><b>Flusso di Validazione (Fail-Fast):</b></p>
	 * <ol>
	 * <li><i>Null & Blank Safety:</i> Se la stringa è nulla, vuota o composta solo da spazi, 
	 * la validazione fallisce immediatamente. Si assume che un codice di imballaggio sia 
	 * un dato obbligatorio nel dominio ADR.</li>
	 * <li><i>Pattern Matching:</i> Verifica la stringa contro la grammatica ADR definita nel 
	 * {@link #ONU_PACKING_CODE_PATTERN}.</li>
	 * </ol>
	 * @param value Il codice o la lista di codici ONU da validare (proveniente dal client/DTO).
	 * @param context Il contesto di validazione fornito da Hibernate Validator.
	 * @return {@code true} se la stringa è valorizzata e rispetta il formato ADR e i limiti del DB, 
	 * {@code false} in caso contrario.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		return ONU_PACKING_CODE_PATTERN.matcher(value).matches();
	}
}
