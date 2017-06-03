package brotherdetjr.pauline.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class Session {
    private volatile Object state;
    private volatile boolean busy;
}
