package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.service.ScheduleAlreadyExistsException;
import cires.dft.remotescheduler.service.ScheduleNotFoundException;
import cires.dft.remotescheduler.solver.NoFeasibleScheduleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the service's failures into useful HTTP responses instead of bare 500s. */
@RestControllerAdvice(assignableTypes = ScheduleRestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(ScheduleNotFoundException.class)
    public ProblemDetail notFound(ScheduleNotFoundException e) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Schedule not found");
        return problem;
    }

    @ExceptionHandler(ScheduleAlreadyExistsException.class)
    public ProblemDetail alreadyExists(ScheduleAlreadyExistsException e) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Schedule already exists");
        problem.setProperty("weekStart", e.getWeekStart().toString());
        return problem;
    }

    /**
     * The constraints cannot be satisfied — too little capacity, or a person with no valid
     * week. That is a configuration problem, so report it as one rather than as a server fault.
     */
    @ExceptionHandler(NoFeasibleScheduleException.class)
    public ProblemDetail infeasible(NoFeasibleScheduleException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("No feasible schedule");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badConfiguration(IllegalArgumentException e) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request or configuration");
        return problem;
    }
}
