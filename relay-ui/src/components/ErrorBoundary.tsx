import { Component, type ReactNode } from 'react';
import { Button } from './ui/Button';

type Props = {
  children: ReactNode;
  /** Changing this (e.g. the route) clears a previous crash. */ resetKey?: string;
};
type State = { failed: boolean };

/** Last line of defence against a white screen. Logs nothing (errors may carry request data). */
export class ErrorBoundary extends Component<Props, State> {
  override state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  override componentDidUpdate(prev: Props) {
    if (this.state.failed && prev.resetKey !== this.props.resetKey)
      this.setState({ failed: false });
  }

  override render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div
        role="alert"
        className="mx-auto mt-16 flex max-w-md flex-col items-center gap-3 px-4 text-center"
      >
        <h1 className="text-lg font-semibold">This page crashed</h1>
        <p className="text-sm text-muted">
          Something unexpected went wrong while rendering. Your connection is unchanged.
        </p>
        <Button onClick={() => this.setState({ failed: false })}>Try again</Button>
      </div>
    );
  }
}
