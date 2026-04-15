import { render, screen } from '@testing-library/react';
import App from './App';

test('renders transaction evaluation form', () => {
  render(<App />);
  expect(screen.getByText(/ImpulseLock/i)).toBeInTheDocument();
  expect(screen.getByText(/Transaction evaluation/i)).toBeInTheDocument();
  expect(screen.getByText(/Evaluation result/i)).toBeInTheDocument();
});
