package eu.europa.ted.efx.model.signatures;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.DateExpression;
import eu.europa.ted.efx.model.expressions.scalar.DurationExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.expressions.scalar.ScalarExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.scalar.TimeExpression;
import eu.europa.ted.efx.model.expressions.sequence.SequenceExpression;

/**
 * The arguments of a call, in order, each with the parameter it is given for.
 *
 * <p>Every argument is parsed from EFX, those of a built-in as much as those of a function or a
 * template declared in EFX, so one list serves them all. The arguments of a resolved call are
 * made by {@link Parameters#match}: each is converted to its parameter, with {@code T} bound, and
 * the typed accessors are what a built-in reads them with when it translates the call. A template
 * block passes its arguments down to the blocks it holds.</p>
 *
 * @param <P> the kind of parameter: declared in the catalogue, or parsed with a declaration
 */
public class ParsedArguments<P extends Parameter> extends LinkedList<ParsedArgument<P>> {

    /**
     * Pairs each parameter with the value at its position.
     *
     * @param parameters the parameter of each position, in order
     * @param values the value of each position, one for each parameter
     */
    public ParsedArguments(List<? extends P> parameters, List<? extends TypedExpression> values) {
        super(IntStream.range(0, parameters.size())
                .mapToObj(position -> new ParsedArgument<P>(parameters.get(position),
                        values.get(position)))
                .collect(Collectors.toList()));
    }

    /**
     * @return the values of the arguments, in order
     */
    public List<TypedExpression> getArgumentValues() {
        return this.stream().map(argument -> argument.value).collect(Collectors.toList());
    }

    /**
     * Converts the argument list to a set of arguments.
     * Uses a LinkedHashSet to maintain argument order.
     *
     * @return a Set of Argument objects
     */
    public Set<ParsedArgument<P>> toSet() {
        return this.stream().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Gives the argument at a position as an expression of its parameter's class.
     *
     * @param <T> the expression class of the parameter at that position, or a supertype of it
     * @param position the position of the argument
     * @param type the expression class of the parameter at that position, or a supertype of it
     * @return the argument
     */
    public <T extends TypedExpression> T at(final int position, final Class<T> type) {
        return type.cast(this.get(position).value);
    }

    /**
     * Gives the expression class an argument was converted to, which for {@code T} or {@code T*} is
     * the class of the value primitive {@code T} is bound to.
     *
     * @param position the position of the argument
     * @return the expression class of the parameter at that position
     */
    public Class<? extends TypedExpression> typeAt(final int position) {
        final ParsedArgument<P> argument = this.get(position);
        return argument.parameter.getType().expressionTypeOf(argument.value);
    }

    /**
     * Gives the sequence class an argument was converted to, for a parameter that is a sequence.
     *
     * @param position the position of the argument
     * @return the sequence class of the parameter at that position
     */
    public Class<? extends SequenceExpression> sequenceTypeAt(final int position) {
        return this.typeAt(position).asSubclass(SequenceExpression.class);
    }

    /**
     * @param position the position of a text argument
     * @return the argument
     */
    public StringExpression stringAt(final int position) {
        return this.at(position, StringExpression.class);
    }

    /**
     * @param position the position of a number argument
     * @return the argument
     */
    public NumericExpression numericAt(final int position) {
        return this.at(position, NumericExpression.class);
    }

    /**
     * @param position the position of an indicator argument
     * @return the argument
     */
    public BooleanExpression booleanAt(final int position) {
        return this.at(position, BooleanExpression.class);
    }

    /**
     * @param position the position of a date argument
     * @return the argument
     */
    public DateExpression dateAt(final int position) {
        return this.at(position, DateExpression.class);
    }

    /**
     * @param position the position of a time argument
     * @return the argument
     */
    public TimeExpression timeAt(final int position) {
        return this.at(position, TimeExpression.class);
    }

    /**
     * @param position the position of a duration argument
     * @return the argument
     */
    public DurationExpression durationAt(final int position) {
        return this.at(position, DurationExpression.class);
    }

    /**
     * @param position the position of a single-value argument
     * @return the argument
     */
    public ScalarExpression scalarAt(final int position) {
        return this.at(position, ScalarExpression.class);
    }

    /**
     * @param position the position of a sequence argument
     * @return the argument
     */
    public SequenceExpression sequenceAt(final int position) {
        return this.at(position, SequenceExpression.class);
    }

    /**
     * @param position the position of a path argument
     * @return the argument
     */
    public PathExpression pathAt(final int position) {
        return this.at(position, PathExpression.class);
    }

    /**
     * Gives the text arguments of a variadic tail.
     *
     * @param position the position of the first argument of the tail
     * @return the arguments from that position to the last
     */
    public List<StringExpression> stringsFrom(final int position) {
        return this.subList(position, this.size()).stream()
                .map(argument -> StringExpression.class.cast(argument.value))
                .collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ParsedArguments<?> other = (ParsedArguments<?>) obj;
        return Objects.equals(this.getArgumentValues(), other.getArgumentValues());
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Objects.hashCode(this.getArgumentValues());
    }
}
