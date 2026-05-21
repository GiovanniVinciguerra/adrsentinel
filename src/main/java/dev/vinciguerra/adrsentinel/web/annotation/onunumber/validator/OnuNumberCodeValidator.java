package dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorOnuNumberCode}.
 * Agisce come scudo architetturale (Edge Validation) per garantire che i codici ONU 
 * (UN Number) forniti in ingresso rispettino rigorosamente la normativa internazionale sui 
 * trasporti pericolosi (ADR), la quale impone l'uso di un formato numerico a 4 cifre.
 * <p><b>Design Architetturale (Strict & Composite Validation):</b></p>
 * A differenza di validatori flessibili, questa classe implementa una logica restrittiva. 
 * Riconoscendo l'assoluta obbligatorietà legale del numero ONU nell'identificazione di 
 * una merce pericolosa, questo validatore fonde la verifica di presenza del dato (assorbendo 
 * il comportamento di {@code @NotBlank}) con la validazione sintattica. L'assenza del dato 
 * viene considerata a tutti gli effetti un'infrazione del dominio.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see ValidatorOnuNumberCode
 */
public class OnuNumberCodeValidator implements ConstraintValidator<ValidatorOnuNumberCode, String> {
	/**
	 * Espressione Regolare (Regex) pre-compilata per l'analisi formale del Numero ONU.
	 * <p><b>Ottimizzazione delle Performance:</b></p>
	 * L'istanziazione come costante {@code static final} assicura che l'automa a stati finiti 
	 * venga compilato in memoria una sola volta all'avvio dell'applicazione (Classloading), 
	 * azzerando l'overhead computazionale durante l'elaborazione del traffico HTTP.
	 * <p><b>Specifiche del Pattern {@code ^\d{4}$}:</b></p>
	 * <ul>
	 * <li>{@code ^} : Asserisce l'inizio esatto della stringa.</li>
	 * <li>{@code \d{4}} : Impone la presenza di esattamente 4 cifre numeriche (es. "1203" per la benzina).</li>
	 * <li>{@code $} : Asserisce la fine esatta della stringa, impedendo l'inserimento di caratteri spuri in coda.</li>
	 * </ul>
	 */
	private final static Pattern ONU_NUMBER_CODE_PATTERN = Pattern.compile("^\\d{4}$");
	
	/**
	 * Esegue la validazione effettiva sul payload in ingresso (Data Flow).
	 * <p><b>Flusso di Esecuzione (Fail-Fast):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge immediatamente 
	 * valori {@code null}, stringhe vuote o composte esclusivamente da spazi bianchi 
	 * (tramite {@code isBlank()}). Questo garantisce l'obbligatorietà intrinseca del campo.</li>
	 * <li><b>Pattern Matching:</b> Sottopone la stringa valorizzata e pulita al motore Regex 
	 * pre-compilato per certificarne l'esatta corrispondenza con il formato normativo a 4 cifre.</li>
	 * </ol>
	 * @param value La stringa rappresentante il codice ONU estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se la stringa è presente e corrisponde esattamente 
	 * a 4 cifre numeriche; {@code false} se il dato è assente, vuoto, o sintatticamente malformato.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		return ONU_NUMBER_CODE_PATTERN.matcher(value).matches();
	}
}
