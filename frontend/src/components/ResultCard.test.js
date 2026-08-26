import { render, screen } from '@testing-library/react';
import ResultCard from './ResultCard';

test('shows the empty hint when there is no result yet', () => {
  render(<ResultCard emptyHint="Evaluate a transaction." />);
  expect(screen.getByText('Evaluate a transaction.')).toBeInTheDocument();
});

test('shows a loading spinner', () => {
  render(<ResultCard loading />);
  expect(screen.getByLabelText(/Loading/i)).toBeInTheDocument();
});

test('shows an error', () => {
  render(<ResultCard error="Something broke." />);
  expect(screen.getByText('Something broke.')).toBeInTheDocument();
});

test('renders the decision, risk score, explanation, and triggered rules', () => {
  render(
    <ResultCard
      result={{
        decisionType: 'DELAY',
        riskScore: 45,
        explanation: 'Night spending restricted.',
        triggeredRules: [{ ruleCode: 'NIGHT_SPENDING', weight: 30, message: 'Outside allowed hours' }],
      }}
    />
  );

  expect(screen.getByText('DELAY')).toBeInTheDocument();
  expect(screen.getByText('45.00')).toBeInTheDocument();
  expect(screen.getByText('Night spending restricted.')).toBeInTheDocument();
  expect(screen.getByText('NIGHT_SPENDING')).toBeInTheDocument();
  expect(screen.getByText('+30')).toBeInTheDocument();
});
